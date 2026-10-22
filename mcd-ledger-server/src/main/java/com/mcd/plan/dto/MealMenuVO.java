package com.mcd.plan.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 门店菜单 VO（query-meals 归一）：分类 + 扁平餐品列表。 */
@Data
public class MealMenuVO {
    private String storeId;
    private List<CategoryVO> categories = new ArrayList<>();
    private List<MealVO> meals = new ArrayList<>();

    @Data
    public static class CategoryVO {
        private String name;
        private String daypart;
        private List<String> mealCodes = new ArrayList<>();
    }
}
