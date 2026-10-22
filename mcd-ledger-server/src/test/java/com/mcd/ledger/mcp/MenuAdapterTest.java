package com.mcd.ledger.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcd.ledger.common.AuditLogger;
import com.mcd.plan.dto.MealDetailVO;
import com.mcd.plan.dto.MealMenuVO;
import com.mcd.plan.dto.MealVO;
// 主理人 23:47 修复：MenuAdapter 已迁入 com.mcd.plan.mcp（包归属对齐 plan 域），
// 本测试保留在 ledger.mcp 包（被测依赖 McpClient/McpToolWhitelist 同包无需导入），仅需显式导入 MenuAdapter。
import com.mcd.plan.mcp.MenuAdapter;
import org.junit.jupiter.api.Test;

/** MenuAdapter 单测：信封解包 + 元字符串→分 + 规格差价解析。 */
class MenuAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final McpClient client = mock(McpClient.class);
    private final MenuAdapter adapter =
            new MenuAdapter(new McpToolWhitelist(mock(AuditLogger.class)), client);

    @Test
    void listMeals_yuanToCents_andTags() throws Exception {
        String envelope = "{\"success\":true,\"code\":200,\"data\":{"
                + "\"categories\":[{\"name\":\"汉堡\",\"daypart\":\"全天\",\"meals\":[{\"code\":\"C1\",\"tags\":[\"辣\"]}]}],"
                + "\"meals\":{\"C1\":{\"name\":\"麦辣鸡腿堡\",\"currentPrice\":\"16\",\"originalPrice\":\"20\","
                + "\"discountType\":\"特价\",\"image\":\"http://img/x.png\",\"isCanWithOrder\":true}}}}";
        String resp = "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                + mapper.writeValueAsString("# 字段说明\n## Original Response\n" + envelope) + "}]}}";
        when(client.call(eq("query-meals"), any())).thenReturn(mapper.readTree(resp).get("result"));

        MealMenuVO vo = adapter.listMeals("S1", "1", "1");
        assertEquals(1, vo.getMeals().size());
        MealVO m = vo.getMeals().get(0);
        assertEquals("麦辣鸡腿堡", m.getName());
        assertEquals(1600L, m.getCurrentPriceCents());  // "16" 元 → 1600 分
        assertEquals(2000L, m.getOriginalPriceCents());  // "20" 元 → 2000 分
        assertEquals("特价", m.getDiscountType());
        assertTrue(m.getCanWithOrder());
        assertEquals(1, m.getTags().size());
        assertEquals("辣", m.getTags().get(0));
        assertEquals(1, vo.getCategories().size());
        assertEquals("汉堡", vo.getCategories().get(0).getName());
    }

    @Test
    void getMealDetail_diffPriceFormatted() throws Exception {
        String envelope = "{\"success\":true,\"code\":200,\"data\":{"
                + "\"code\":\"C1\",\"name\":\"麦辣鸡腿堡\",\"price\":\"33.5\","
                + "\"rounds\":[{\"id\":\"R1\",\"name\":\"规格\",\"quantity\":1,\"choices\":["
                + "{\"code\":\"CH1\",\"name\":\"加辣\",\"quantity\":1,\"diffPrice\":\"+ ¥0\",\"isDefault\":true,\"supportModify\":true},"
                + "{\"code\":\"CH2\",\"name\":\"加蛋\",\"quantity\":1,\"diffPrice\":\"¥3\",\"isDefault\":false,\"supportModify\":true}]}]}}";
        String resp = "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                + mapper.writeValueAsString(envelope) + "}]}}";
        when(client.call(eq("query-meal-detail"), any())).thenReturn(mapper.readTree(resp).get("result"));

        MealDetailVO vo = adapter.getMealDetail("S1", "C1", "1", "1");
        assertEquals(3350L, vo.getPriceCents());        // "33.5" → 3350 分
        assertEquals(1, vo.getRounds().size());
        var choices = vo.getRounds().get(0).getChoices();
        assertEquals(2, choices.size());
        assertEquals(0L, choices.get(0).getDiffPriceCents());  // "+ ¥0" → 0
        assertEquals(300L, choices.get(1).getDiffPriceCents()); // "¥3" → 300
        assertTrue(choices.get(0).getIsDefault());
    }

    @Test
    void listMeals_emptyData_ok() throws Exception {
        String envelope = "{\"success\":true,\"code\":200,\"data\":{}}";
        String resp = "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                + mapper.writeValueAsString(envelope) + "}]}}";
        when(client.call(eq("query-meals"), any())).thenReturn(mapper.readTree(resp).get("result"));
        MealMenuVO vo = adapter.listMeals("S1", "1", "1");
        assertTrue(vo.getMeals().isEmpty());
        assertEquals("S1", vo.getStoreId()); // storeId 仍回填
    }
}
