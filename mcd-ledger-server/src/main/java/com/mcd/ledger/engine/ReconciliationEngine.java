package com.mcd.ledger.engine;

import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.ledger.dto.PendingItem;
import com.mcd.ledger.dto.PersonRecon;
import com.mcd.ledger.dto.ProjectRecon;
import com.mcd.ledger.dto.ReconciliationReport;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 三方恒等校验（设计 §4.4 Step4 / P2）：
 *  ordersTotal == personsTotal == projectsTotal，diffCents 必须为 0；
 *  尾差 ≠ 0 时阻断导出（LEDGER_DIFF_NOT_ZERO）。
 */
@Component
public class ReconciliationEngine {

    /** 汇总三方合计与尾差，组装报表。diffCents = ordersTotal − personsTotal（projectsTotal 恒等于 personsTotal）。 */
    public ReconciliationReport assemble(long ordersTotal, long personsTotal,
                                         List<ProjectRecon> projects,
                                         List<PersonRecon> persons,
                                         List<PendingItem> pending) {
        long projectsTotal = projects.stream().mapToLong(ProjectRecon::getTotalCents).sum();
        ReconciliationReport report = new ReconciliationReport();
        report.setOrdersTotalCents(ordersTotal);
        report.setPersonsTotalCents(personsTotal);
        report.setProjectsTotalCents(projectsTotal);
        report.setDiffCents(ordersTotal - personsTotal);
        report.setPersons(persons);
        report.setProjects(projects);
        report.setPendingItems(pending);
        return report;
    }

    /** 尾差非 0 即阻断导出。 */
    public void assertExportable(ReconciliationReport report) {
        if (!report.isBalanced()) {
            throw BizException.of(ErrorCode.LEDGER_DIFF_NOT_ZERO,
                    "三方尾差非 0（" + report.getDiffCents() + " 分），存在待确认项，已阻断导出");
        }
    }
}
