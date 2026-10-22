package com.mcd.ledger.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** McpEnvelope 解包：覆盖 structuredContent 直取、content[].text 内嵌信封（正则路径）、数组 data。 */
class McpEnvelopeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 无 structuredContent，信封嵌在 markdown「## Original Response」之后（对齐 01-order-list 实测）。 */
    @Test
    void unwrap_embeddedEnvelopeAfterMarkdown() throws Exception {
        String text = "# API Response Information\n\n## Response Structure\n- **data.list**: 订单列表\n"
                + "## Original Response\n\n{\"success\":true,\"code\":200,\"message\":\"ok\","
                + "\"data\":{\"list\":[{\"orderId\":\"X1\",\"realTotalAmount\":\"40\"}]}}";
        String resp = "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                + mapper.writeValueAsString(text) + "}]}}";
        JsonNode result = mapper.readTree(resp).get("result");

        JsonNode data = McpEnvelope.unwrap(result);
        assertTrue(data.has("list"));
        assertEquals("X1", data.get("list").get(0).get("orderId").asText());
        assertEquals("40", data.get("list").get(0).get("realTotalAmount").asText());
    }

    /** structuredContent 即信封本体，直取其 data（对齐 09-calculate-price 实测）。 */
    @Test
    void unwrap_structuredContentShortCircuit() throws Exception {
        String resp = "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"...\"}],"
                + "\"structuredContent\":{\"success\":true,\"code\":200,"
                + "\"data\":{\"price\":3350,\"productList\":[{\"subtotal\":3350}]}}}}";
        JsonNode result = mapper.readTree(resp).get("result");

        JsonNode data = McpEnvelope.unwrap(result);
        assertEquals(3350, data.get("price").asInt());
        assertEquals(3350, data.get("productList").get(0).get("subtotal").asInt());
    }

    /** data 为数组，信封嵌在 markdown 表格单元格尾部（对齐 10-store-coupons 实测）。 */
    @Test
    void unwrap_arrayDataEmbeddedInTableCell() throws Exception {
        String text = "## 输出格式\n|券ID|券码|\n|:---:|:---:|\n|{data[].couponId}|"
                + "{\"success\":true,\"code\":200,\"data\":[{\"title\":\"美式7折\",\"couponId\":\"C1\"}]}";
        String resp = "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                + mapper.writeValueAsString(text) + "}]}}";
        JsonNode result = mapper.readTree(resp).get("result");

        JsonNode data = McpEnvelope.unwrap(result);
        assertTrue(data.isArray());
        assertEquals("美式7折", data.get(0).get("title").asText());
    }

    @Test
    void isSuccess_checksEnvelope() throws Exception {
        String okResp = "{\"result\":{\"structuredContent\":{\"success\":true,\"code\":200,\"data\":{}}}}";
        assertTrue(McpEnvelope.isSuccess(mapper.readTree(okResp).get("result")));
        String failResp = "{\"result\":{\"structuredContent\":{\"success\":false,\"code\":500,\"data\":{}}}}";
        assertFalse(McpEnvelope.isSuccess(mapper.readTree(failResp).get("result")));
    }
}
