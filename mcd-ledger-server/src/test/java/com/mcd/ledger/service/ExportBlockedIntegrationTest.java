package com.mcd.ledger.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mcd.ledger.domain.ItemAllocation;
import com.mcd.ledger.domain.LedgerOrder;
import com.mcd.ledger.domain.OrderItem;
import com.mcd.ledger.dto.CreateLedgerRequest;
import com.mcd.ledger.mapper.ItemAllocationMapper;
import com.mcd.ledger.mapper.LedgerOrderMapper;
import com.mcd.ledger.mapper.MealLedgerMapper;
import com.mcd.ledger.mapper.OrderItemMapper;
import com.mcd.ledger.mapper.ParticipantMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * QA 补测（严过关）：尾差≠0 时导出阻断的真实可达性（设计 NF-2 / §6 export.csv 409）。
 * 走完整 HTTP 路径 GET /api/v1/ledgers/{id}/export.csv：
 *  - 不平餐账 → 409 + code=LEDGER_DIFF_NOT_ZERO（证明不是死代码）；
 *  - 平行餐账 → 200 text/csv + 三方合计校验行（对照组，证明阻断是条件性的）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:qablock;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class ExportBlockedIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LedgerService service;
    @Autowired
    private MealLedgerMapper ledgerMapper;
    @Autowired
    private LedgerOrderMapper orderMapper;
    @Autowired
    private OrderItemMapper itemMapper;
    @Autowired
    private ItemAllocationMapper allocMapper;
    @Autowired
    private ParticipantMapper participantMapper;

    /** 建一本只有 1 个参与人（挂到沙盒项目 1）的餐账，返回 ledgerId */
    private Long newLedger(String title) {
        CreateLedgerRequest req = new CreateLedgerRequest();
        req.setTitle(title);
        req.setOperatorName("QA");
        req.setSubsidyLimitCents(3500L);
        req.setIncludeDeliveryInSubsidy(false);
        CreateLedgerRequest.ParticipantSpec p = new CreateLedgerRequest.ParticipantSpec();
        p.setDisplayName("甲工");
        p.setPersonRef("jia");
        p.setProjectId(1L); // 沙盒数据已载入 project_ref id=1
        req.getParticipants().add(p);
        return service.createInternal(req);
    }

    /** 插一笔订单 + 1 个整份归甲工的餐品 */
    private void seedOrder(Long ledgerId, String mcpOrderId, long paid, long discount, long delivery,
                           long itemTotal, long participantId) {
        LedgerOrder o = new LedgerOrder();
        o.setLedgerId(ledgerId);
        o.setMcpOrderId(mcpOrderId);
        o.setChannel("测试");
        o.setOrderStatus("COMPLETED");
        o.setDataConfidence("VERIFIED_HISTORICAL");
        o.setPaidTotalCents(paid);
        o.setDiscountCents(discount);
        o.setDeliveryFeeCents(delivery);
        o.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(o);

        OrderItem it = new OrderItem();
        it.setLedgerOrderId(o.getId());
        it.setExternalItemId("TEST-1");
        it.setName("测试餐品");
        it.setQuantity(1);
        it.setItemTotalCents(itemTotal);
        it.setAllocationType("PERSONAL");
        it.setConfidence("VERIFIED_HISTORICAL");
        itemMapper.insert(it);

        ItemAllocation a = new ItemAllocation();
        a.setOrderItemId(it.getId());
        a.setParticipantId(participantId);
        a.setWeight(1);
        a.setAllocatedCents(itemTotal);
        allocMapper.insert(a);
    }

    private Long firstParticipantId(Long ledgerId) {
        return participantMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<com.mcd.ledger.domain.Participant>()
                .eq(com.mcd.ledger.domain.Participant::getLedgerId, ledgerId))
                .get(0).getId();
    }

    @Test
    @DisplayName("尾差 2000 分：export.csv 返回 409 + LEDGER_DIFF_NOT_ZERO（阻断真实可达）")
    void unbalancedLedger_exportBlocked409() throws Exception {
        Long ledgerId = newLedger("QA 不平餐账");
        Long pid = firstParticipantId(ledgerId);
        // 食品 9000 − 优惠 1000 + 配送 0 = 8000，但实付记 10000 → 尾差 2000
        seedOrder(ledgerId, "QA-UNBALANCED-1", 10000L, -1000L, 0L, 9000L, pid);

        // 服务层先确认尾差现形
        var report = service.getReconciliation(ledgerId);
        org.junit.jupiter.api.Assertions.assertEquals(2000L, report.getDiffCents());
        org.junit.jupiter.api.Assertions.assertFalse(report.isBalanced());

        mockMvc.perform(get("/api/v1/ledgers/{id}/export.csv", ledgerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEDGER_DIFF_NOT_ZERO"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("尾差")));
    }

    @Test
    @DisplayName("对照组：平行餐账 export.csv 返回 200 text/csv 且含三方合计校验行")
    void balancedLedger_export200() throws Exception {
        Long ledgerId = newLedger("QA 平行餐账");
        Long pid = firstParticipantId(ledgerId);
        // 食品 9000 − 优惠 1000 = 8000 = 实付 8000 → 尾差 0
        seedOrder(ledgerId, "QA-BALANCED-1", 8000L, -1000L, 0L, 9000L, pid);

        mockMvc.perform(get("/api/v1/ledgers/{id}/export.csv", ledgerId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(result -> {
                    String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                    org.junit.jupiter.api.Assertions.assertTrue(csv.contains("[三方合计校验]"), "缺少三方合计校验行");
                    org.junit.jupiter.api.Assertions.assertTrue(csv.contains("尾差(分),0"), "尾差应为 0");
                    org.junit.jupiter.api.Assertions.assertTrue(csv.contains("人员分摊合计(分),8000"));
                });
    }

    @Test
    @DisplayName("沙盒 ledger 1（基准数据集）导出 200：证明基准数据本身可导出")
    void sandboxLedger1_export200() throws Exception {
        mockMvc.perform(get("/api/v1/ledgers/{id}/export.csv", 1L))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                    org.junit.jupiter.api.Assertions.assertTrue(csv.contains("订单实付合计(分),22800"));
                    org.junit.jupiter.api.Assertions.assertTrue(csv.contains("人员分摊合计(分),22800"));
                    org.junit.jupiter.api.Assertions.assertTrue(csv.contains("项目归集合计(分),22800"));
                    org.junit.jupiter.api.Assertions.assertTrue(csv.contains("尾差(分),0"));
                });
    }
}
