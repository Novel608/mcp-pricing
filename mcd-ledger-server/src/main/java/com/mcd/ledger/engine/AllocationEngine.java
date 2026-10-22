package com.mcd.ledger.engine;

import com.mcd.ledger.common.Money;
import com.mcd.ledger.engine.model.ReconInput;
import com.mcd.ledger.engine.model.ReconInput.EngineAlloc;
import com.mcd.ledger.engine.model.ReconInput.EngineItem;
import com.mcd.ledger.engine.model.ReconInput.EngineOrder;
import com.mcd.ledger.engine.model.ReconInput.EngineParticipant;
import com.mcd.ledger.engine.model.ReconInput.EngineProject;
import com.mcd.ledger.dto.PendingItem;
import com.mcd.ledger.dto.PersonRecon;
import com.mcd.ledger.dto.ProjectRecon;
import com.mcd.ledger.dto.ReconciliationReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 四级核算流水线（设计 §4.4）：
 *   Step1 单订单内闭环：餐品归属（PERSONAL/SHARED 按权重 PennyAllocator；UNASSIGNED 进 pending），
 *         该单 discount/delivery 以本订单内各人餐品小计为权重分摊；
 *   Step2 跨单汇总到人 + 餐补测算（subsidyBase = 餐品净额，可选含配送费；within=min(base,limit)，exceed=total−within）；
 *   Step3 按 projectId 归集；
 *   Step4 三方恒等校验（ ReconciliationEngine ）。
 *
 * 关键：订单内分摊顺序 = 参与人在本单的「首次出现顺序」（跨 items/allocations），
 * 保证 PennyAllocator 平局派发确定性，从而精确复现设计 §9 基准。
 */
@Component
public class AllocationEngine {

    private final ReconciliationEngine reconciliationEngine = new ReconciliationEngine();

    public ReconciliationReport reconcile(ReconInput in) {
        // 参与人累加器（保持输入顺序，用于输出）
        LinkedHashMap<Long, PersonAcc> accById = new LinkedHashMap<>();
        for (EngineParticipant p : in.participants()) {
            accById.put(p.participantId(), new PersonAcc(p));
        }

        List<PendingItem> pending = new ArrayList<>();
        long ordersTotal = 0L;

        for (EngineOrder order : in.orders()) {
            ordersTotal += nz(order.paidTotalCents());

            // Step1a: 本单各人餐品小计（首次出现顺序 → LinkedHashMap）
            LinkedHashMap<Long, Long> foodByPerson = new LinkedHashMap<>();
            for (EngineItem item : order.items()) {
                if (item.type() == AllocationType.UNASSIGNED
                        || item.allocations() == null || item.allocations().isEmpty()) {
                    pending.add(pendingItem(order, item));
                    continue;
                }
                List<Integer> weights = new ArrayList<>();
                for (EngineAlloc a : item.allocations()) {
                    weights.add(a.weight());
                }
                List<Long> shares = PennyAllocator.allocate(nz(item.itemTotalCents()), weights);
                for (int i = 0; i < item.allocations().size(); i++) {
                    Long pid = item.allocations().get(i).participantId();
                    foodByPerson.merge(pid, shares.get(i), Long::sum);
                }
            }
            if (foodByPerson.isEmpty()) {
                continue; // 整单均未分配
            }

            // Step1b: 优惠/配送费按本单餐品小计权重分摊（负数优惠走 PennyAllocator 符号还原）
            List<Long> personOrder = new ArrayList<>(foodByPerson.keySet());
            List<Integer> foodWeights = new ArrayList<>();
            for (Long pid : personOrder) {
                foodWeights.add((int) Math.max(0L, Math.min(foodByPerson.get(pid), Integer.MAX_VALUE)));
            }
            List<Long> discountShares = PennyAllocator.allocate(nz(order.discountCents()), foodWeights);
            List<Long> deliveryShares = PennyAllocator.allocate(nz(order.deliveryFeeCents()), foodWeights);

            for (int i = 0; i < personOrder.size(); i++) {
                Long pid = personOrder.get(i);
                PersonAcc acc = accById.get(pid);
                if (acc == null) {
                    continue; // 分摊到非本账参与人：忽略（数据异常，尾差会暴露）
                }
                long food = foodByPerson.get(pid);
                long disc = discountShares.get(i);
                long deliv = deliveryShares.get(i);
                long payable = food + disc + deliv;
                acc.totalCents += payable;
                acc.subsidyBaseCents += food + disc + (in.includeDeliveryInSubsidy() ? deliv : 0L);
                acc.contributions.add(new Contribution(order.mcpOrderId(), food, disc, deliv, payable));
            }
        }

        // Step2: 餐补测算 + 人员行
        Long limit = in.subsidyLimitCents();
        List<PersonRecon> persons = new ArrayList<>();
        long personsTotal = 0L;
        for (EngineParticipant p : in.participants()) {
            PersonAcc acc = accById.get(p.participantId());
            long within = (limit == null) ? acc.subsidyBaseCents : Math.min(acc.subsidyBaseCents, limit);
            long exceed = acc.totalCents - within;
            PersonRecon pr = new PersonRecon();
            pr.setParticipantId(p.participantId());
            pr.setDisplayName(p.displayName());
            pr.setProjectId(p.projectId());
            pr.setTotalCents(acc.totalCents);
            pr.setSubsidyBaseCents(acc.subsidyBaseCents);
            pr.setWithinStandardCents(within);
            pr.setExceedStandardCents(exceed);
            pr.setFormulaLines(buildFormula(acc, limit, within, exceed));
            persons.add(pr);
            personsTotal += acc.totalCents;
        }

        // Step3: 按项目归集
        LinkedHashMap<Long, ProjectRecon> projById = new LinkedHashMap<>();
        for (EngineProject p : in.projects()) {
            ProjectRecon pr = new ProjectRecon();
            pr.setProjectId(p.projectId());
            pr.setName(p.name());
            pr.setCode(p.code());
            pr.setTotalCents(0L);
            pr.setWithinStandardCents(0L);
            pr.setExceedStandardCents(0L);
            projById.put(p.projectId(), pr);
        }
        ProjectRecon unassigned = null;
        for (PersonRecon pr : persons) {
            ProjectRecon proj = projById.get(pr.getProjectId());
            if (proj == null) {
                if (unassigned == null) {
                    unassigned = new ProjectRecon();
                    unassigned.setProjectId(null);
                    unassigned.setName("未归集");
                    unassigned.setCode("UNASSIGNED");
                    unassigned.setTotalCents(0L);
                    unassigned.setWithinStandardCents(0L);
                    unassigned.setExceedStandardCents(0L);
                }
                proj = unassigned;
            }
            proj.setTotalCents(proj.getTotalCents() + pr.getTotalCents());
            proj.setWithinStandardCents(proj.getWithinStandardCents() + pr.getWithinStandardCents());
            proj.setExceedStandardCents(proj.getExceedStandardCents() + pr.getExceedStandardCents());
        }
        List<ProjectRecon> projects = new ArrayList<>(projById.values());
        if (unassigned != null) {
            projects.add(unassigned);
        }

        // Step4: 三方恒等
        return reconciliationEngine.assemble(ordersTotal, personsTotal, projects, persons, pending);
    }

    private PendingItem pendingItem(EngineOrder order, EngineItem item) {
        PendingItem pi = new PendingItem();
        pi.setOrderId(order.orderId());
        pi.setItemId(item.itemId());
        pi.setName(item.name());
        pi.setItemTotalCents(nz(item.itemTotalCents()));
        return pi;
    }

    private List<String> buildFormula(PersonAcc acc, Long limit, long within, long exceed) {
        List<String> lines = new ArrayList<>();
        for (Contribution c : acc.contributions) {
            StringBuilder sb = new StringBuilder();
            sb.append("订单 ").append(c.mcpOrderId).append("：餐品 ").append(Money.formatYuan(c.food))
              .append(" − 优惠 ").append(Money.formatYuan(-c.discount))
              .append(" + 配送 ").append(Money.formatYuan(c.delivery))
              .append(" = ").append(Money.formatYuan(c.payable));
            lines.add(sb.toString());
        }
        StringBuilder sum = new StringBuilder();
        sum.append("合计 ").append(Money.formatYuan(acc.totalCents))
           .append(" = 标准内 ").append(Money.formatYuan(within))
           .append(" + 超标 ").append(Money.formatYuan(exceed))
           .append("（餐补基数 ").append(Money.formatYuan(acc.subsidyBaseCents));
        if (limit != null) {
            sum.append("，上限 ").append(Money.formatYuan(limit));
        }
        sum.append("）");
        lines.add(sum.toString());
        return lines;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static final class PersonAcc {
        final EngineParticipant participant;
        long totalCents = 0L;
        long subsidyBaseCents = 0L;
        final List<Contribution> contributions = new ArrayList<>();

        PersonAcc(EngineParticipant participant) {
            this.participant = participant;
        }
    }

    private record Contribution(String mcpOrderId, long food, long discount, long delivery, long payable) {}
}
