package com.mcd.ledger.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcd.ledger.common.Money;
import com.mcd.plan.dto.MealDetailVO;
import com.mcd.plan.dto.MealMenuVO;
import com.mcd.plan.dto.MealVO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 菜单适配层（A1 联调，query-meals / query-meal-detail）。
 * 金额为元字符串 → Money.parseYuanToCents 转分；响应经 McpEnvelope.unwrap 解包；
 * Caffeine 缓存：菜单 10min、详情 30min（设计 §7.4）。沙盒模式下 McpClient 由 SandboxMcpClient 顶替，本类零改动。
 */
@Component
public class MenuAdapter {

    private final McpToolWhitelist whitelist;
    private final McpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public MenuAdapter(McpToolWhitelist whitelist, McpClient client) {
        this.whitelist = whitelist;
        this.client = client;
    }

    /** 代理 query-meals：门店菜单（分类 + 扁平餐品）。 */
    @Cacheable(cacheNames = "meals", key = "#storeId")
    public MealMenuVO listMeals(String storeId, String orderType, String beType) {
        whitelist.assertReadable("query-meals");
        ObjectNode args = mapper.createObjectNode();
        args.put("storeCode", storeId);
        args.put("orderType", orderType == null ? "1" : orderType);
        args.put("beType", beType == null ? "1" : beType);
        JsonNode data = McpEnvelope.unwrap(client.call("query-meals", args));
        return toMenuVO(storeId, data);
    }

    /** 代理 query-meal-detail：餐品/套餐详情（规格轮次）。 */
    @Cacheable(cacheNames = "mealDetail", key = "#storeId + ':' + #itemId")
    public MealDetailVO getMealDetail(String storeId, String itemId, String orderType, String beType) {
        whitelist.assertReadable("query-meal-detail");
        ObjectNode args = mapper.createObjectNode();
        args.put("code", itemId);
        args.put("storeCode", storeId);
        args.put("orderType", orderType == null ? "1" : orderType);
        args.put("beType", beType == null ? "1" : beType);
        JsonNode data = McpEnvelope.unwrap(client.call("query-meal-detail", args));
        return toDetailVO(data);
    }

    private MealMenuVO toMenuVO(String storeId, JsonNode data) {
        MealMenuVO vo = new MealMenuVO();
        vo.setStoreId(storeId);

        // tags 归并：categories[].meals[].tags → code→tags
        Map<String, List<String>> tagsByCode = new LinkedHashMap<>();
        JsonNode categories = data.get("categories");
        if (categories != null && categories.isArray()) {
            for (JsonNode cat : categories) {
                MealMenuVO.CategoryVO cv = new MealMenuVO.CategoryVO();
                cv.setName(text(cat, "name"));
                cv.setDaypart(text(cat, "daypart"));
                JsonNode meals = cat.get("meals");
                if (meals != null && meals.isArray()) {
                    for (JsonNode m : meals) {
                        String code = text(m, "code");
                        cv.getMealCodes().add(code);
                        List<String> tags = new ArrayList<>();
                        JsonNode t = m.get("tags");
                        if (t != null && t.isArray()) {
                            t.forEach(x -> tags.add(x.asText()));
                        }
                        tagsByCode.put(code, tags);
                    }
                }
                vo.getCategories().add(cv);
            }
        }

        // meals 为 map：code → {name,currentPrice,originalPrice,discountType,image,isCanWithOrder}
        JsonNode mealsMap = data.get("meals");
        if (mealsMap != null && mealsMap.isObject()) {
            mealsMap.fields().forEachRemaining(e -> {
                String code = e.getKey();
                JsonNode info = e.getValue();
                MealVO mv = new MealVO();
                mv.setCode(code);
                mv.setName(text(info, "name"));
                mv.setCurrentPriceCents(yuanCents(info, "currentPrice"));
                mv.setOriginalPriceCents(yuanCents(info, "originalPrice"));
                mv.setImage(text(info, "image"));
                mv.setDiscountType(text(info, "discountType"));
                mv.setCanWithOrder(info.path("isCanWithOrder").isBoolean()
                        ? info.get("isCanWithOrder").asBoolean() : null);
                mv.setTags(tagsByCode.getOrDefault(code, new ArrayList<>()));
                vo.getMeals().add(mv);
            });
        }
        return vo;
    }

    private MealDetailVO toDetailVO(JsonNode data) {
        MealDetailVO vo = new MealDetailVO();
        vo.setCode(text(data, "code"));
        vo.setName(text(data, "name"));
        vo.setPriceCents(yuanCents(data, "price"));
        JsonNode rounds = data.get("rounds");
        if (rounds != null && rounds.isArray()) {
            for (JsonNode r : rounds) {
                MealDetailVO.RoundVO rv = new MealDetailVO.RoundVO();
                rv.setId(text(r, "id"));
                rv.setName(text(r, "name"));
                rv.setQuantity(r.path("quantity").isNumber() ? r.get("quantity").asInt() : null);
                JsonNode choices = r.get("choices");
                if (choices != null && choices.isArray()) {
                    for (JsonNode c : choices) {
                        MealDetailVO.ChoiceVO cv = new MealDetailVO.ChoiceVO();
                        cv.setCode(text(c, "code"));
                        cv.setName(text(c, "name"));
                        cv.setQuantity(c.path("quantity").isNumber() ? c.get("quantity").asInt() : null);
                        cv.setDiffPriceCents(yuanCents(c, "diffPrice"));
                        cv.setIsDefault(c.path("isDefault").isBoolean() ? c.get("isDefault").asBoolean() : null);
                        cv.setSupportModify(c.path("supportModify").isBoolean() ? c.get("supportModify").asBoolean() : null);
                        rv.getChoices().add(cv);
                    }
                }
                vo.getRounds().add(rv);
            }
        }
        return vo;
    }

    private static String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }

    /** 菜单侧金额：元（字符串/数字）→ 分。 */
    private static Long yuanCents(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber() || v.isTextual()) {
            try {
                return Money.parseYuanToCents(v.asText());
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }
}
