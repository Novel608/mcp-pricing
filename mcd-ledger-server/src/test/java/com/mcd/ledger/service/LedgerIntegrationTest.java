package com.mcd.ledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcd.ledger.domain.MealLedger;
import com.mcd.ledger.dto.LedgerDetailVO;
import com.mcd.ledger.dto.PersonRecon;
import com.mcd.ledger.dto.ProjectRecon;
import com.mcd.ledger.dto.ReconciliationReport;
import com.mcd.ledger.mapper.LedgerOrderMapper;
import com.mcd.ledger.mapper.MealLedgerMapper;
import com.mcd.ledger.mapper.OrderItemMapper;
import com.mcd.ledger.mapper.ParticipantMapper;
import com.mcd.ledger.mapper.ProjectRefMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 全栈集成测试：真实 H2 + schema.sql + data-sandbox.sql + MyBatis-Plus 映射 + 服务编排 + 引擎。
 * 验证沙盒基准三方恒等 22800、各人/项目精确值，以及餐补规则变更后恒等保持（对应验收 #3）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ledgertest;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE")
class LedgerIntegrationTest {

    @Autowired
    private LedgerService service;

    @Autowired
    private MealLedgerMapper ledgerMapper;
    @Autowired
    private LedgerOrderMapper orderMapper;
    @Autowired
    private OrderItemMapper itemMapper;
    @Autowired
    private ParticipantMapper participantMapper;
    @Autowired
    private ProjectRefMapper projectRefMapper;

    @Test
    void sandboxSeeded_mappingWorks() {
        MealLedger ledger = ledgerMapper.selectById(1L);
        assertNotNull(ledger, "沙盒 ledger id=1 应被 data-sandbox.sql 载入");
        assertEquals(3500L, ledger.getSubsidyLimitCents());
        assertEquals(Boolean.FALSE, ledger.getIncludeDeliveryInSubsidy());
        assertEquals(2, orderMapper.selectCount(null));
        assertEquals(8, itemMapper.selectCount(null));
        assertEquals(6, participantMapper.selectCount(null));
        assertEquals(2, projectRefMapper.selectCount(null));
    }

    @Test
    void reconciliation_threeWayIdentity_22800() {
        ReconciliationReport r = service.getReconciliation(1L);
        assertEquals(22800L, r.getOrdersTotalCents());
        assertEquals(22800L, r.getPersonsTotalCents());
        assertEquals(22800L, r.getProjectsTotalCents());
        assertEquals(0L, r.getDiffCents());
        assertTrue(r.getPendingItems().isEmpty());
    }

    @Test
    void perPersonTotals_matchSandbox() {
        ReconciliationReport r = service.getReconciliation(1L);
        assertEquals(4316L, person(r, 1L).getTotalCents()); // 林工
        assertEquals(4316L, person(r, 2L).getTotalCents()); // 陈工
        assertEquals(3878L, person(r, 3L).getTotalCents()); // 王工
        assertEquals(3878L, person(r, 4L).getTotalCents()); // 赵工
        assertEquals(3206L, person(r, 5L).getTotalCents()); // 孙工
        assertEquals(3206L, person(r, 6L).getTotalCents()); // 周工
        assertEquals(4078L, person(r, 1L).getSubsidyBaseCents());
        assertEquals(816L, person(r, 1L).getExceedStandardCents());
    }

    @Test
    void projects_matchSandbox() {
        ReconciliationReport r = service.getReconciliation(1L);
        assertEquals(15716L, project(r, 1L).getTotalCents()); // A 核心账务重构
        assertEquals(7084L, project(r, 2L).getTotalCents());  // B AI助手接入
    }

    @Test
    void getLedger_aggregatesAll() {
        LedgerDetailVO vo = service.getLedger(1L);
        assertNotNull(vo.getMealTime());
        assertEquals(2, vo.getOrders().size());
        assertEquals(6, vo.getParticipants().size());
        assertEquals(2, vo.getProjects().size());
        assertEquals(0L, vo.getReconciliation().getDiffCents());
        // 订单1 有 5 个餐品，其中麦乐鸡 SHARED 有 4 条分摊
        var order1 = vo.getOrders().stream()
                .filter(o -> "MCD-20261022-01".equals(o.getMcpOrderId())).findFirst().orElseThrow();
        assertEquals(5, order1.getItems().size());
        var nuggets = order1.getItems().stream()
                .filter(i -> "麦乐鸡20块".equals(i.getName())).findFirst().orElseThrow();
        assertEquals("SHARED", nuggets.getAllocationType());
        assertEquals(4, nuggets.getAllocations().size());
    }

    @Test
    void subsidyRuleChange_identityStillHolds() {
        // 验收 #3：改餐补上限后，三方合计仍恒等，标准内/超标随之联动
        service.setSubsidyRule(1L, 4000L, false);
        try {
            ReconciliationReport r = service.getReconciliation(1L);
            assertEquals(22800L, r.getOrdersTotalCents());
            assertEquals(22800L, r.getPersonsTotalCents());
            assertEquals(22800L, r.getProjectsTotalCents());
            assertEquals(0L, r.getDiffCents());
            // 林工 base 4078，limit 4000 → within 4000，exceed 4316-4000=316
            assertEquals(4000L, person(r, 1L).getWithinStandardCents());
            assertEquals(316L, person(r, 1L).getExceedStandardCents());
        } finally {
            service.setSubsidyRule(1L, 3500L, false); // 还原，避免污染同上下文其他用例
        }
    }

    @Test
    void exportCsv_containsIdentityCheckRow() {
        String csv = service.exportCsv(1L);
        assertTrue(csv.contains("三方合计校验"));
        assertTrue(csv.contains("22800"));
        assertTrue(csv.contains("尾差"));
    }

    private static PersonRecon person(ReconciliationReport r, long id) {
        return r.getPersons().stream().filter(p -> p.getParticipantId() == id).findFirst().orElseThrow();
    }

    private static ProjectRecon project(ReconciliationReport r, long id) {
        return r.getProjects().stream().filter(p -> p.getProjectId() != null && p.getProjectId() == id)
                .findFirst().orElseThrow();
    }
}
