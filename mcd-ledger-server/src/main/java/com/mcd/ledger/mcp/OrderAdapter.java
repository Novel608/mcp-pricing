package com.mcd.ledger.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcd.ledger.common.Money;
import com.mcd.ledger.domain.LedgerOrder;
import com.mcd.ledger.domain.OrderItem;
import com.mcd.ledger.engine.AllocationType;
import com.mcd.ledger.mcp.dto.OrderCard;
import com.mcd.ledger.mcp.dto.OrderSnapshotResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * MCP 订单适配层（设计 §3 / §5「唯一允许依赖 MCP 原始字段的模块」）。
 *   listOrders()        → order-list 摘要（脱敏可选订单卡片，自动过滤已取消）
 *   getOrderDetail(id)  → query-order 深度明细 → LedgerOrder + OrderItem + DataConfidence 三态
 *
 * A1 联调实测：order-list / query-order 的金额字段均为**元字符串**
 * （realTotalAmount:"40"、totalAmount:"22"、deliveryPrice:"6"、productPrice:"16"），
 * 故本类一律走 Money.parseYuanToCents 转分。（calculate-price 才是分整数，另见试算适配层。）
 * 响应信封经 McpEnvelope.unwrap 解包。
 */
@Component
public class OrderAdapter {

    private final McpToolWhitelist whitelist;
    private final McpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrderAdapter(McpToolWhitelist whitelist, McpClient client) {
        this.whitelist = whitelist;
        this.client = client;
    }

    /** 代理 order-list，返回脱敏可选订单卡片（已过滤取消/退款单）。 */
    public List<OrderCard> listOrders() {
        whitelist.assertReadable("order-list");
        JsonNode data = McpEnvelope.unwrap(client.call("order-list", mapper.createObjectNode()));
        JsonNode arr = data.has("list") ? data.get("list") : data;
        List<OrderCard> cards = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode o : arr) {
                OrderCard c = new OrderCard();
                c.setMcpOrderId(text(o, "orderId", "orderNo", "orderCode"));
                c.setChannel(text(o, "orderType", "beType", "takeWay"));
                c.setOrderStatus(text(o, "orderStatus", "status"));
                c.setPaidTotalCents(yuanCents(o, "realTotalAmount", "totalAmount", "payAmount"));
                c.setOrderTime(text(o, "createTime", "orderTime", "createdAt"));
                if (isCancelled(c.getOrderStatus())) {
                    continue; // 自动过滤已取消/退款
                }
                cards.add(c);
            }
        }
        return cards;
    }

    /** query-order 深度明细 → 订单快照 + 餐品 + 置信度。 */
    public OrderSnapshotResult getOrderDetail(String mcpOrderId) {
        whitelist.assertReadable("query-order");
        ObjectNode args = mapper.createObjectNode();
        args.put("orderId", mcpOrderId);
        JsonNode data = McpEnvelope.unwrap(client.call("query-order", args));

        LedgerOrder order = new LedgerOrder();
        order.setMcpOrderId(mcpOrderId);
        order.setChannel(text(data, "takeWay", "orderType", "channel"));
        order.setOrderStatus(text(data, "orderStatus", "status"));
        order.setPaidTotalCents(yuanCents(data, "realTotalAmount", "totalAmount", "payAmount"));
        Long discount = yuanCents(data, "totalDiscountAmount", "discountAmount", "couponAmount");
        order.setDiscountCents(discount == null ? null : -discount); // 优惠存负
        order.setDeliveryFeeCents(yuanCents(data, "realDeliveryPrice", "deliveryPrice", "packingPrice"));

        List<OrderItem> items = new ArrayList<>();
        boolean hasCombo = false;
        JsonNode products = data.get("orderProductList");
        if (products != null && products.isArray()) {
            for (JsonNode p : products) {
                OrderItem oi = new OrderItem();
                oi.setExternalItemId(text(p, "productCode", "productId", "code"));
                oi.setName(text(p, "productName", "name", "title"));
                int qty = intVal(p, 1, "quantity", "qty", "count");
                oi.setQuantity(qty);
                Long unitCents = yuanCents(p, "price", "currentPrice", "salePrice");
                // price 视为单价（元），行小计 = 单价 × 数量
                oi.setItemTotalCents(unitCents == null ? null : unitCents * qty);
                oi.setAllocationType(AllocationType.UNASSIGNED.name()); // 导入时未分配，进待确认区
                JsonNode combo = p.get("comboItemList");
                if (combo != null && combo.isArray() && !combo.isEmpty()) {
                    hasCombo = true; // 套餐无单品拆解价 → 置信度降级
                }
                items.add(oi);
            }
        }

        DataConfidence conf = determineConfidence(order, items, hasCombo);
        order.setDataConfidence(conf.name());
        for (OrderItem oi : items) {
            oi.setConfidence(conf.name());
        }

        OrderSnapshotResult res = new OrderSnapshotResult();
        res.setOrder(order);
        res.setItems(items);
        return res;
    }

    /** 三态置信度（设计 §3.3）：含套餐(无拆解价)或字段不全 → AGREED_ALLOCATION；取消/缺额 → PENDING_CONFIRMATION。 */
    private DataConfidence determineConfidence(LedgerOrder order, List<OrderItem> items, boolean hasCombo) {
        if (isCancelled(order.getOrderStatus())) {
            return DataConfidence.PENDING_CONFIRMATION;
        }
        boolean hasPaid = order.getPaidTotalCents() != null;
        boolean itemsPriced = !items.isEmpty()
                && items.stream().allMatch(i -> i.getItemTotalCents() != null);
        if (hasPaid && itemsPriced && !hasCombo) {
            return DataConfidence.VERIFIED_HISTORICAL;
        }
        if (hasPaid) {
            return DataConfidence.AGREED_ALLOCATION;
        }
        return DataConfidence.PENDING_CONFIRMATION;
    }

    private static boolean isCancelled(String status) {
        if (status == null) {
            return false;
        }
        String s = status.toUpperCase();
        return s.contains("CANCEL") || s.contains("REFUND")
                || s.contains("取消") || s.contains("退款") || s.contains("已取消");
    }

    private static String text(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }

    /** 订单侧金额：元（字符串或数字）→ 分。一律 Money.parseYuanToCents。 */
    private static Long yuanCents(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isNumber()) {
                return Money.parseYuanToCents(v.asText());
            }
            if (v.isTextual() && !v.asText().isBlank()) {
                try {
                    return Money.parseYuanToCents(v.asText());
                } catch (Exception ignore) {
                    // 尝试下一个键
                }
            }
        }
        return null;
    }

    private static int intVal(JsonNode node, int def, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && v.isNumber()) {
                return v.asInt();
            }
        }
        return def;
    }
}
