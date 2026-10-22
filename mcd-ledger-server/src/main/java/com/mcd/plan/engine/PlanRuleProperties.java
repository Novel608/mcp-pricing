package com.mcd.plan.engine;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 选餐推荐规则表（application.yml：mcd.plan.combos / mcd.plan.thresholds）。
 *
 * <p><b>性质声明（设计 附录 A8 / L3 红线）</b>：本表只描述「什么条件触发什么优惠」的**活动信息**
 * （哪个套餐由哪些单品组成、满多少减多少），<b>不含任何价格</b>。所有价格一律取自官方
 * `query-meals` 菜单参考价或官方 `calculate-price` 核价结果，因此不属于 L3 禁止的
 * 「本地菜单估算」——系统内不存在也不会出现 PRICE_SOURCE_LOCAL_MENU。
 */
@Component
@ConfigurationProperties(prefix = "mcd.plan")
public class PlanRuleProperties {

    /** R1 套餐组合候选（套餐 code 与其覆盖的单品构成）。 */
    private List<ComboDefinition> combos = new ArrayList<>();

    /** R2 满减档位（门槛 / 优惠）。 */
    private List<ThresholdDefinition> thresholds = new ArrayList<>();

    public List<ComboDefinition> getCombos() {
        return combos;
    }

    public void setCombos(List<ComboDefinition> combos) {
        this.combos = combos == null ? new ArrayList<>() : combos;
    }

    public List<ThresholdDefinition> getThresholds() {
        return thresholds;
    }

    public void setThresholds(List<ThresholdDefinition> thresholds) {
        this.thresholds = thresholds == null ? new ArrayList<>() : thresholds;
    }

    /** 套餐定义：comboItemId 必须是官方菜单中存在的套餐 code。 */
    @Data
    public static class ComboDefinition {
        private String comboItemId;
        private String name;
        private List<Cover> covers = new ArrayList<>();
    }

    /** 套餐覆盖的单品构成（quantity 为「一套」内该单品的份数）。 */
    @Data
    public static class Cover {
        private String itemId;
        private String name;
        private int quantity = 1;
    }

    /** 满减档位：thresholdCents 门槛（分），discountCents 优惠（分）。 */
    @Data
    public static class ThresholdDefinition {
        private long thresholdCents;
        private long discountCents;
        private String title;
    }
}
