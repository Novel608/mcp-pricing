package com.mcd.ledger.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP tools/call 响应解包（A1 联调实测结构）。
 *
 * 真实返回：result.content[0].text = 「字段说明 markdown + 内嵌 JSON 信封」，
 * 信封形如 {"success":true,"code":200,"message","datetime","traceId","data":...}；
 * 「## Original Response」头不总存在，且信封可能出现在 markdown 表格单元格等任意位置。
 * result.structuredContent 通常就是该信封本体。
 *
 * 解包策略（鲁棒）：
 *   1) structuredContent 存在且含 data → 取 structuredContent.data；
 *   2) 否则在 content[].text 中用正则定位 `{"success"` 起点，解析出信封并取 data；
 *   3) 兜底：text 本身是纯 JSON 则直接用；再不行返回原 result。
 */
public final class McpEnvelope {

    private static final Pattern ENVELOPE_START = Pattern.compile("\\{\\s*\"success\"");
    private static final ObjectMapper LENIENT = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private McpEnvelope() {}

    /** 解出业务载荷（信封的 data 节点，可能为对象或数组）。 */
    public static JsonNode unwrap(JsonNode result) {
        if (result == null) {
            return LENIENT.createObjectNode();
        }
        JsonNode sc = result.get("structuredContent");
        if (sc != null && sc.isObject() && sc.has("data")) {
            return sc.get("data");
        }
        JsonNode content = result.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode c : content) {
                String text = c.path("text").asText(null);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                JsonNode data = extractData(text);
                if (data != null) {
                    return data;
                }
                try {
                    JsonNode t = LENIENT.readTree(text);
                    if (t != null && (t.isObject() || t.isArray())) {
                        return t;
                    }
                } catch (Exception ignore) {
                    // 非纯 JSON，继续
                }
            }
        }
        return result;
    }

    /** 判断信封是否成功（success==true 且 code==200）。 */
    public static boolean isSuccess(JsonNode result) {
        if (result == null) {
            return false;
        }
        JsonNode sc = result.get("structuredContent");
        if (sc != null && sc.isObject()) {
            return sc.path("success").asBoolean(false) && sc.path("code").asInt(-1) == 200;
        }
        JsonNode content = result.get("content");
        if (content != null && content.isArray() && !content.isEmpty()) {
            String text = content.get(0).path("text").asText("");
            Matcher m = ENVELOPE_START.matcher(text);
            if (m.find()) {
                try {
                    JsonNode env = LENIENT.readTree(text.substring(m.start()));
                    return env.path("success").asBoolean(false) && env.path("code").asInt(-1) == 200;
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
        return false;
    }

    private static JsonNode extractData(String text) {
        Matcher m = ENVELOPE_START.matcher(text);
        if (m.find()) {
            try {
                JsonNode env = LENIENT.readTree(text.substring(m.start()));
                if (env.has("data")) {
                    return env.get("data");
                }
                return env;
            } catch (Exception ignore) {
                // 信封解析失败，交兜底
            }
        }
        return null;
    }
}
