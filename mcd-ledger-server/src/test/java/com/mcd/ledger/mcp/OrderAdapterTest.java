package com.mcd.ledger.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcd.ledger.common.AuditLogger;
import com.mcd.ledger.domain.LedgerOrder;
import com.mcd.ledger.domain.OrderItem;
import com.mcd.ledger.mcp.dto.OrderSnapshotResult;
import org.junit.jupiter.api.Test;

/**
 * OrderAdapter 单元测试：验证 A1 修复——订单侧金额（元字符串）经 Money.parseYuanToCents 转分，
 * 信封经 content[].text 正则路径解包，含套餐降级为 AGREED_ALLOCATION。
 */
class OrderAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final McpClient client = mock(McpClient.class);
    private final OrderAdapter adapter =
            new OrderAdapter(new McpToolWhitelist(mock(AuditLogger.class)), client);

    /** query-order：金额为元字符串；无 structuredContent，走内嵌信封正则路径。 */
    @Test
    void getOrderDetail_yuanStringsToCents() throws Exception {
        String envelope = "{\"success\":true,\"code\":200,\"data\":{"
                + "\"orderId\":\"X1\",\"takeWay\":\"麦乐送\",\"orderStatus\":\"已完成\","
                + "\"realTotalAmount\":\"40\",\"totalAmount\":\"46\",\"totalDiscountAmount\":\"6\","
                + "\"deliveryPrice\":\"6\",\"realDeliveryPrice\":\"6\",\"productPrice\":\"34\","
                + "\"orderProductList\":["
                + "  {\"productCode\":\"P1\",\"productName\":\"麦辣鸡腿堡\",\"quantity\":2,\"price\":\"16\",\"comboItemList\":[]},"
                + "  {\"productCode\":\"C1\",\"productName\":\"麦辣三件套\",\"quantity\":1,\"price\":\"22\","
                + "     \"comboItemList\":[{\"itemName\":\"可乐\",\"itemQuantity\":1}]}"
                + "]}}";
        String text = "# 字段说明 markdown\n## Original Response\n" + envelope;
        String resp = "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                + mapper.writeValueAsString(text) + "}]}}";
        when(client.call(eq("query-order"), any())).thenReturn(mapper.readTree(resp).get("result"));

        OrderSnapshotResult snap = adapter.getOrderDetail("X1");
        LedgerOrder o = snap.getOrder();

        assertEquals(4000L, o.getPaidTotalCents());      // realTotalAmount "40" → 4000 分
        assertEquals(-600L, o.getDiscountCents());        // totalDiscountAmount "6" → -600 分（负）
        assertEquals(600L, o.getDeliveryFeeCents());      // realDeliveryPrice "6" → 600 分
        // 含套餐 → 整单降级 AGREED_ALLOCATION
        assertEquals(DataConfidence.AGREED_ALLOCATION.name(), o.getDataConfidence());

        assertEquals(2, snap.getItems().size());
        OrderItem burger = snap.getItems().get(0);
        assertEquals("麦辣鸡腿堡", burger.getName());
        assertEquals(2, burger.getQuantity());
        assertEquals(3200L, burger.getItemTotalCents());  // 单价 "16" × 2 = 3200 分
        OrderItem combo = snap.getItems().get(1);
        assertEquals(2200L, combo.getItemTotalCents());    // 单价 "22" × 1 = 2200 分
    }

    /** 无套餐、字段齐全 → VERIFIED_HISTORICAL。 */
    @Test
    void getOrderDetail_noCombo_verifiedHistorical() throws Exception {
        String envelope = "{\"success\":true,\"code\":200,\"data\":{"
                + "\"orderId\":\"X2\",\"takeWay\":\"到店自取\",\"orderStatus\":\"已完成\","
                + "\"realTotalAmount\":\"33.5\",\"totalDiscountAmount\":\"0\",\"realDeliveryPrice\":\"0\","
                + "\"orderProductList\":[{\"productCode\":\"P9\",\"productName\":\"薯条\",\"quantity\":1,"
                + "\"price\":\"33.5\",\"comboItemList\":[]}]}}";
        String resp = "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                + mapper.writeValueAsString(envelope) + "}]}}";
        when(client.call(eq("query-order"), any())).thenReturn(mapper.readTree(resp).get("result"));

        OrderSnapshotResult snap = adapter.getOrderDetail("X2");
        assertEquals(3350L, snap.getOrder().getPaidTotalCents()); // "33.5" → 3350 分
        assertEquals(0L, snap.getOrder().getDiscountCents());
        assertEquals(DataConfidence.VERIFIED_HISTORICAL.name(), snap.getOrder().getDataConfidence());
    }

    /** order-list：data.list[] 元字符串金额 + 取消单过滤。 */
    @Test
    void listOrders_yuanAndCancelFilter() throws Exception {
        String envelope = "{\"success\":true,\"code\":200,\"data\":{\"list\":["
                + "{\"orderId\":\"A\",\"orderType\":\"麦乐送\",\"orderStatus\":\"已完成\",\"realTotalAmount\":\"40\",\"createTime\":\"2026-10-22 20:00\"},"
                + "{\"orderId\":\"B\",\"orderType\":\"到店自取\",\"orderStatus\":\"订单已取消\",\"realTotalAmount\":\"22\",\"createTime\":\"2026-10-22 19:00\"}"
                + "]}}";
        String resp = "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                + mapper.writeValueAsString(envelope) + "}]}}";
        when(client.call(eq("order-list"), any())).thenReturn(mapper.readTree(resp).get("result"));

        var cards = adapter.listOrders();
        assertEquals(1, cards.size());              // 取消单被过滤
        assertEquals("A", cards.get(0).getMcpOrderId());
        assertEquals(4000L, cards.get(0).getPaidTotalCents()); // "40" → 4000 分
    }

    /** 空 data（实测该账号无历史订单）→ 空列表，不报错。 */
    @Test
    void listOrders_emptyData_ok() throws Exception {
        String envelope = "{\"success\":true,\"code\":200,\"data\":{}}";
        String resp = "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                + mapper.writeValueAsString(envelope) + "}]}}";
        when(client.call(eq("order-list"), any())).thenReturn(mapper.readTree(resp).get("result"));
        assertEquals(0, adapter.listOrders().size());
    }
}
