package com.mcd.plan.web;

import com.mcd.ledger.mcp.CouponAdapter;
import com.mcd.ledger.mcp.MenuAdapter;
import com.mcd.plan.dto.CouponVO;
import com.mcd.plan.dto.MealDetailVO;
import com.mcd.plan.dto.MealMenuVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MCP 菜单/券代理（/api/v1/mcp，设计 §3.4 #4/#5）。沙盒模式由 SandboxMcpClient 供数，无需 Token。 */
@RestController
public class McpMenuController {

    private final MenuAdapter menuAdapter;
    private final CouponAdapter couponAdapter;

    public McpMenuController(MenuAdapter menuAdapter, CouponAdapter couponAdapter) {
        this.menuAdapter = menuAdapter;
        this.couponAdapter = couponAdapter;
    }

    @GetMapping("/api/v1/mcp/meals")
    public MealMenuVO meals(@RequestParam String storeId,
                            @RequestParam(required = false, defaultValue = "1") String orderType,
                            @RequestParam(required = false, defaultValue = "1") String beType) {
        return menuAdapter.listMeals(storeId, orderType, beType);
    }

    @GetMapping("/api/v1/mcp/meals/{itemId}")
    public MealDetailVO mealDetail(@PathVariable String itemId,
                                   @RequestParam String storeId,
                                   @RequestParam(required = false, defaultValue = "1") String orderType,
                                   @RequestParam(required = false, defaultValue = "1") String beType) {
        return menuAdapter.getMealDetail(storeId, itemId, orderType, beType);
    }

    @GetMapping("/api/v1/mcp/coupons")
    public List<CouponVO> coupons(@RequestParam String storeId,
                                  @RequestParam(required = false, defaultValue = "1") String orderType,
                                  @RequestParam(required = false, defaultValue = "1") String beType) {
        return couponAdapter.listStoreCoupons(storeId, orderType, beType);
    }
}
