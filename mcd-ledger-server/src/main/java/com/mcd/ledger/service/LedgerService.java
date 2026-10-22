package com.mcd.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mcd.ledger.common.AuditLogger;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.ledger.domain.LedgerOrder;
import com.mcd.ledger.domain.LedgerExportSnapshot;
import com.mcd.ledger.domain.MealLedger;
import com.mcd.ledger.domain.OrderItem;
import com.mcd.ledger.domain.ItemAllocation;
import com.mcd.ledger.domain.Participant;
import com.mcd.ledger.domain.ProjectRef;
import com.mcd.ledger.dto.AddOrdersRequest;
import com.mcd.ledger.dto.AllocVO;
import com.mcd.ledger.dto.CreateLedgerRequest;
import com.mcd.ledger.dto.ItemVO;
import com.mcd.ledger.dto.LedgerDetailVO;
import com.mcd.ledger.dto.OrderVO;
import com.mcd.ledger.dto.ParticipantVO;
import com.mcd.ledger.dto.ProjectVO;
import com.mcd.ledger.dto.ReconciliationReport;
import com.mcd.ledger.dto.SetAllocationRequest;
import com.mcd.ledger.engine.AllocationEngine;
import com.mcd.ledger.engine.AllocationType;
import com.mcd.ledger.engine.PennyAllocator;
import com.mcd.ledger.engine.ReconciliationEngine;
import com.mcd.ledger.engine.model.ReconInput;
import com.mcd.ledger.engine.model.ReconInput.EngineAlloc;
import com.mcd.ledger.engine.model.ReconInput.EngineItem;
import com.mcd.ledger.engine.model.ReconInput.EngineOrder;
import com.mcd.ledger.engine.model.ReconInput.EngineParticipant;
import com.mcd.ledger.engine.model.ReconInput.EngineProject;
import com.mcd.ledger.mcp.OrderAdapter;
import com.mcd.ledger.mapper.ItemAllocationMapper;
import com.mcd.ledger.mapper.LedgerExportSnapshotMapper;
import com.mcd.ledger.mapper.LedgerOrderMapper;
import com.mcd.ledger.mapper.MealLedgerMapper;
import com.mcd.ledger.mapper.OrderItemMapper;
import com.mcd.ledger.mapper.ParticipantMapper;
import com.mcd.ledger.mapper.ProjectRefMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 对账应用服务（编排层，不放业务计算；计算在 engine）。 */
@Service
public class LedgerService {

    private final MealLedgerMapper ledgerMapper;
    private final LedgerOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final ItemAllocationMapper allocMapper;
    private final ParticipantMapper participantMapper;
    private final ProjectRefMapper projectRefMapper;
    private final LedgerExportSnapshotMapper exportSnapshotMapper;
    private final AllocationEngine allocationEngine;
    private final ReconciliationEngine reconciliationEngine;
    private final OrderAdapter orderAdapter;
    private final AuditLogger auditLogger;

    public LedgerService(MealLedgerMapper ledgerMapper, LedgerOrderMapper orderMapper,
                         OrderItemMapper itemMapper, ItemAllocationMapper allocMapper,
                         ParticipantMapper participantMapper, ProjectRefMapper projectRefMapper,
                         LedgerExportSnapshotMapper exportSnapshotMapper,
                         AllocationEngine allocationEngine, ReconciliationEngine reconciliationEngine,
                         OrderAdapter orderAdapter, AuditLogger auditLogger) {
        this.ledgerMapper = ledgerMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.allocMapper = allocMapper;
        this.participantMapper = participantMapper;
        this.projectRefMapper = projectRefMapper;
        this.exportSnapshotMapper = exportSnapshotMapper;
        this.allocationEngine = allocationEngine;
        this.reconciliationEngine = reconciliationEngine;
        this.orderAdapter = orderAdapter;
        this.auditLogger = auditLogger;
    }

    // ---------- 创建 ----------

    public Long createInternal(CreateLedgerRequest req) {
        MealLedger ledger = new MealLedger();
        ledger.setTitle(req.getTitle());
        ledger.setMealTime(parseTime(req.getMealTime()));
        ledger.setOperatorName(req.getOperatorName());
        ledger.setSubsidyLimitCents(req.getSubsidyLimitCents());
        ledger.setIncludeDeliveryInSubsidy(Boolean.TRUE.equals(req.getIncludeDeliveryInSubsidy()));
        ledger.setStatus("DRAFT");
        ledger.setCreatedAt(LocalDateTime.now());
        ledgerMapper.insert(ledger);
        Long ledgerId = ledger.getId();

        for (CreateLedgerRequest.ProjectSpec ps : req.getProjects()) {
            ProjectRef pr = new ProjectRef();
            pr.setLedgerId(ledgerId);
            pr.setName(ps.getName());
            pr.setCode(ps.getCode());
            projectRefMapper.insert(pr);
        }
        int idx = 0;
        for (CreateLedgerRequest.ParticipantSpec ps : req.getParticipants()) {
            Participant p = new Participant();
            p.setLedgerId(ledgerId);
            p.setDisplayName(ps.getDisplayName());
            p.setPersonRef(ps.getPersonRef());
            p.setProjectId(ps.getProjectId());
            p.setSortOrder(ps.getSortOrder() != null ? ps.getSortOrder() : idx);
            participantMapper.insert(p);
            idx++;
        }
        return ledgerId;
    }

    // ---------- 读取 ----------

    public LedgerDetailVO getLedger(Long id) {
        LedgerData data = loadLedgerData(id);
        LedgerDetailVO vo = new LedgerDetailVO();
        MealLedger ledger = data.ledger;
        vo.setId(ledger.getId());
        vo.setTitle(ledger.getTitle());
        vo.setMealTime(ledger.getMealTime() == null ? null : ledger.getMealTime().toString());
        vo.setOperatorName(ledger.getOperatorName());
        vo.setSubsidyLimitCents(ledger.getSubsidyLimitCents());
        vo.setIncludeDeliveryInSubsidy(Boolean.TRUE.equals(ledger.getIncludeDeliveryInSubsidy()));
        vo.setStatus(ledger.getStatus());
        vo.setOrders(buildOrderVOs(data));
        vo.setParticipants(buildParticipantVOs(data.participants));
        vo.setProjects(buildProjectVOs(data.projects));
        vo.setReconciliation(reconcile(data));
        return vo;
    }

    public ReconciliationReport getReconciliation(Long id) {
        return reconcile(loadLedgerData(id));
    }

    // ---------- 选入订单（幂等 + 过滤取消） ----------

    public List<OrderVO> addOrders(Long id, List<String> mcpOrderIds) {
        requireLedger(id);
        List<OrderVO> added = new ArrayList<>();
        Map<Long, List<ItemAllocation>> emptyAllocs = new HashMap<>();
        for (String mcpOrderId : mcpOrderIds) {
            Long exist = orderMapper.selectCount(new LambdaQueryWrapper<LedgerOrder>()
                    .eq(LedgerOrder::getLedgerId, id).eq(LedgerOrder::getMcpOrderId, mcpOrderId));
            if (exist != null && exist > 0) {
                continue; // 幂等：重复 mcpOrderId 跳过
            }
            var snap = orderAdapter.getOrderDetail(mcpOrderId);
            LedgerOrder o = snap.getOrder();
            if (isCancelled(o.getOrderStatus())) {
                continue; // 自动过滤已取消/退款
            }
            o.setLedgerId(id);
            o.setCreatedAt(LocalDateTime.now());
            orderMapper.insert(o);
            for (OrderItem it : snap.getItems()) {
                it.setLedgerOrderId(o.getId());
                itemMapper.insert(it);
            }
            added.add(toOrderVO(o, snap.getItems(), emptyAllocs));
        }
        auditLogger.record(id, "ADD_ORDERS", String.join(",", mcpOrderIds), null, null, "system");
        return added;
    }

    // ---------- 餐品归属 ----------

    public void setAllocation(Long id, Long itemId, SetAllocationRequest req) {
        requireLedger(id);
        OrderItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw BizException.of(ErrorCode.LEDGER_NOT_FOUND, "餐品不存在: " + itemId);
        }
        LedgerOrder order = orderMapper.selectById(item.getLedgerOrderId());
        if (order == null || !id.equals(order.getLedgerId())) {
            throw BizException.of(ErrorCode.LEDGER_NOT_FOUND, "餐品不属于该餐账");
        }

        List<SetAllocationRequest.AllocSpec> specs = req.getAllocations();
        AllocationType type;
        if (specs == null || specs.isEmpty()) {
            type = AllocationType.UNASSIGNED;
        } else if (specs.size() == 1) {
            type = AllocationType.PERSONAL;
        } else {
            type = AllocationType.SHARED;
        }
        item.setAllocationType(type.name());
        itemMapper.updateById(item);

        allocMapper.delete(new LambdaQueryWrapper<ItemAllocation>()
                .eq(ItemAllocation::getOrderItemId, itemId));
        if (specs != null && !specs.isEmpty()) {
            List<Integer> weights = new ArrayList<>();
            for (SetAllocationRequest.AllocSpec s : specs) {
                weights.add(s.getWeight() == null ? 1 : s.getWeight());
            }
            long total = item.getItemTotalCents() == null ? 0L : item.getItemTotalCents();
            List<Long> shares = PennyAllocator.allocate(total, weights);
            for (int i = 0; i < specs.size(); i++) {
                ItemAllocation a = new ItemAllocation();
                a.setOrderItemId(itemId);
                a.setParticipantId(specs.get(i).getParticipantId());
                a.setWeight(weights.get(i));
                a.setAllocatedCents(shares.get(i));
                allocMapper.insert(a);
            }
        }
        auditLogger.record(id, "SET_ALLOCATION", String.valueOf(itemId), null, type.name(), "system");
    }

    // ---------- 参与人项目 ----------

    public void setParticipantProject(Long id, Long participantId, Long projectId) {
        requireLedger(id);
        Participant p = participantMapper.selectById(participantId);
        if (p == null || !id.equals(p.getLedgerId())) {
            throw BizException.of(ErrorCode.LEDGER_NOT_FOUND, "参与人不存在: " + participantId);
        }
        p.setProjectId(projectId);
        participantMapper.updateById(p);
        auditLogger.record(id, "SET_PARTICIPANT_PROJECT", String.valueOf(participantId),
                null, String.valueOf(projectId), "system");
    }

    // ---------- 餐补规则 ----------

    public void setSubsidyRule(Long id, Long limitCents, Boolean includeDelivery) {
        requireLedger(id);
        MealLedger l = ledgerMapper.selectById(id);
        if (limitCents != null) {
            l.setSubsidyLimitCents(limitCents);
        }
        if (includeDelivery != null) {
            l.setIncludeDeliveryInSubsidy(includeDelivery);
        }
        ledgerMapper.updateById(l);
        auditLogger.record(id, "SET_SUBSIDY_RULE", null,
                null, "limit=" + limitCents + ",includeDelivery=" + includeDelivery, "system");
    }

    // ---------- CSV 导出（尾差≠0 阻断） ----------

    public String exportCsv(Long id) {
        LedgerData data = loadLedgerData(id);
        ReconciliationReport report = reconcile(data);
        reconciliationEngine.assertExportable(report);

        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF"); // BOM，便于 Excel 识别 UTF-8 中文
        MealLedger l = data.ledger;
        sb.append("麦麦餐帐 — 费用对账明细\n");
        sb.append(row("餐账标题", l.getTitle()));
        sb.append(row("餐次时间", l.getMealTime() == null ? "" : l.getMealTime().toString()));
        sb.append(row("操作人", l.getOperatorName()));
        sb.append(row("餐补上限(分)", String.valueOf(l.getSubsidyLimitCents())));
        sb.append(row("餐补含配送费", String.valueOf(Boolean.TRUE.equals(l.getIncludeDeliveryInSubsidy()))));
        sb.append(row("生成时间", OffsetDateTime.now().toString()));
        sb.append("\n");

        sb.append("[订单来源]\n");
        sb.append("订单号,渠道,状态,置信度,实付(分),优惠(分),配送费(分)\n");
        for (LedgerOrder o : data.orders) {
            sb.append(csv(o.getMcpOrderId())).append(',').append(csv(o.getChannel())).append(',')
              .append(csv(o.getOrderStatus())).append(',').append(csv(o.getDataConfidence())).append(',')
              .append(o.getPaidTotalCents()).append(',').append(o.getDiscountCents()).append(',')
              .append(o.getDeliveryFeeCents()).append('\n');
        }
        sb.append("\n");

        sb.append("[人员分摊]\n");
        sb.append("参与人,项目ID,合计(分),餐补基数(分),标准内(分),超标(分)\n");
        for (var p : report.getPersons()) {
            sb.append(csv(p.getDisplayName())).append(',').append(p.getProjectId()).append(',')
              .append(p.getTotalCents()).append(',').append(p.getSubsidyBaseCents()).append(',')
              .append(p.getWithinStandardCents()).append(',').append(p.getExceedStandardCents()).append('\n');
        }
        sb.append("\n");

        sb.append("[项目归集]\n");
        sb.append("项目,合计(分),标准内(分),超标(分)\n");
        for (var p : report.getProjects()) {
            sb.append(csv(p.getName())).append(',').append(p.getTotalCents()).append(',')
              .append(p.getWithinStandardCents()).append(',').append(p.getExceedStandardCents()).append('\n');
        }
        sb.append("\n");

        sb.append("[三方合计校验]\n");
        sb.append(row("订单实付合计(分)", String.valueOf(report.getOrdersTotalCents())));
        sb.append(row("人员分摊合计(分)", String.valueOf(report.getPersonsTotalCents())));
        sb.append(row("项目归集合计(分)", String.valueOf(report.getProjectsTotalCents())));
        sb.append(row("尾差(分)", String.valueOf(report.getDiffCents())));
        sb.append("\n");

        sb.append("[规则说明]\n");
        sb.append("金额单位为分；优惠以负数列示；最大余额法分摊，尾差按余数降序、下标升序派发。\n");

        // 留痕导出快照
        LedgerExportSnapshot snap = new LedgerExportSnapshot();
        snap.setLedgerId(id);
        snap.setVersion(1);
        snap.setTotalsJson("{\"orders\":" + report.getOrdersTotalCents()
                + ",\"persons\":" + report.getPersonsTotalCents()
                + ",\"projects\":" + report.getProjectsTotalCents()
                + ",\"diff\":" + report.getDiffCents() + "}");
        snap.setCreatedAt(LocalDateTime.now());
        exportSnapshotMapper.insert(snap);

        return sb.toString();
    }

    // ---------- 内部：加载与装配 ----------

    private ReconciliationReport reconcile(LedgerData data) {
        return allocationEngine.reconcile(buildReconInput(data));
    }

    private ReconInput buildReconInput(LedgerData data) {
        List<EngineOrder> engineOrders = new ArrayList<>();
        for (LedgerOrder o : data.orders) {
            List<EngineItem> eitems = new ArrayList<>();
            for (OrderItem it : data.itemsByOrder.getOrDefault(o.getId(), List.of())) {
                List<EngineAlloc> eallocs = new ArrayList<>();
                for (ItemAllocation a : data.allocsByItem.getOrDefault(it.getId(), List.of())) {
                    eallocs.add(new EngineAlloc(a.getParticipantId(), a.getWeight() == null ? 0 : a.getWeight()));
                }
                eitems.add(new EngineItem(it.getId(), it.getName(), it.getItemTotalCents(),
                        resolveType(it.getAllocationType(), eallocs), eallocs));
            }
            engineOrders.add(new EngineOrder(o.getId(), o.getMcpOrderId(), o.getPaidTotalCents(),
                    o.getDiscountCents(), o.getDeliveryFeeCents(), eitems));
        }
        List<EngineParticipant> eparts = new ArrayList<>();
        for (Participant p : data.participants) {
            eparts.add(new EngineParticipant(p.getId(), p.getDisplayName(), p.getProjectId(), p.getSortOrder()));
        }
        List<EngineProject> eprojs = new ArrayList<>();
        for (ProjectRef p : data.projects) {
            eprojs.add(new EngineProject(p.getId(), p.getName(), p.getCode()));
        }
        return new ReconInput(data.ledger.getSubsidyLimitCents(),
                Boolean.TRUE.equals(data.ledger.getIncludeDeliveryInSubsidy()),
                engineOrders, eparts, eprojs);
    }

    private AllocationType resolveType(String stored, List<EngineAlloc> allocs) {
        if (allocs.isEmpty()) {
            return AllocationType.UNASSIGNED;
        }
        if (stored != null && !stored.isBlank()) {
            try {
                AllocationType t = AllocationType.valueOf(stored);
                if (t != AllocationType.UNASSIGNED) {
                    return t;
                }
            } catch (IllegalArgumentException ignore) {
                // 落到按数量推断
            }
        }
        return allocs.size() == 1 ? AllocationType.PERSONAL : AllocationType.SHARED;
    }

    private LedgerData loadLedgerData(Long id) {
        MealLedger ledger = requireLedger(id);
        List<LedgerOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<LedgerOrder>()
                .eq(LedgerOrder::getLedgerId, id).orderByAsc(LedgerOrder::getId));

        Map<Long, List<OrderItem>> itemsByOrder = new HashMap<>();
        Map<Long, List<ItemAllocation>> allocsByItem = new HashMap<>();
        if (!orders.isEmpty()) {
            List<Long> orderIds = orders.stream().map(LedgerOrder::getId).toList();
            List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                    .in(OrderItem::getLedgerOrderId, orderIds).orderByAsc(OrderItem::getId));
            for (OrderItem it : items) {
                itemsByOrder.computeIfAbsent(it.getLedgerOrderId(), k -> new ArrayList<>()).add(it);
            }
            List<Long> itemIds = items.stream().map(OrderItem::getId).toList();
            if (!itemIds.isEmpty()) {
                List<ItemAllocation> allocs = allocMapper.selectList(new LambdaQueryWrapper<ItemAllocation>()
                        .in(ItemAllocation::getOrderItemId, itemIds).orderByAsc(ItemAllocation::getId));
                for (ItemAllocation a : allocs) {
                    allocsByItem.computeIfAbsent(a.getOrderItemId(), k -> new ArrayList<>()).add(a);
                }
            }
        }

        List<Participant> participants = participantMapper.selectList(new LambdaQueryWrapper<Participant>()
                .eq(Participant::getLedgerId, id)
                .orderByAsc(Participant::getSortOrder).orderByAsc(Participant::getId));
        List<ProjectRef> projects = projectRefMapper.selectList(new LambdaQueryWrapper<ProjectRef>()
                .eq(ProjectRef::getLedgerId, id).orderByAsc(ProjectRef::getId));

        return new LedgerData(ledger, orders, itemsByOrder, allocsByItem, participants, projects);
    }

    private List<OrderVO> buildOrderVOs(LedgerData data) {
        List<OrderVO> vos = new ArrayList<>();
        for (LedgerOrder o : data.orders) {
            vos.add(toOrderVO(o, data.itemsByOrder.getOrDefault(o.getId(), List.of()), data.allocsByItem));
        }
        return vos;
    }

    private OrderVO toOrderVO(LedgerOrder o, List<OrderItem> items, Map<Long, List<ItemAllocation>> allocsByItem) {
        OrderVO vo = new OrderVO();
        vo.setId(o.getId());
        vo.setMcpOrderId(o.getMcpOrderId());
        vo.setChannel(o.getChannel());
        vo.setOrderStatus(o.getOrderStatus());
        vo.setDataConfidence(o.getDataConfidence());
        vo.setPaidTotalCents(o.getPaidTotalCents());
        vo.setDiscountCents(o.getDiscountCents());
        vo.setDeliveryFeeCents(o.getDeliveryFeeCents());
        for (OrderItem it : items) {
            ItemVO iv = new ItemVO();
            iv.setId(it.getId());
            iv.setExternalItemId(it.getExternalItemId());
            iv.setName(it.getName());
            iv.setQuantity(it.getQuantity());
            iv.setItemTotalCents(it.getItemTotalCents());
            iv.setAllocationType(it.getAllocationType());
            iv.setConfidence(it.getConfidence());
            for (ItemAllocation a : allocsByItem.getOrDefault(it.getId(), List.of())) {
                AllocVO av = new AllocVO();
                av.setParticipantId(a.getParticipantId());
                av.setWeight(a.getWeight());
                av.setAllocatedCents(a.getAllocatedCents());
                iv.getAllocations().add(av);
            }
            vo.getItems().add(iv);
        }
        return vo;
    }

    private List<ParticipantVO> buildParticipantVOs(List<Participant> participants) {
        List<ParticipantVO> vos = new ArrayList<>();
        for (Participant p : participants) {
            ParticipantVO v = new ParticipantVO();
            v.setId(p.getId());
            v.setDisplayName(p.getDisplayName());
            v.setPersonRef(p.getPersonRef());
            v.setProjectId(p.getProjectId());
            v.setSortOrder(p.getSortOrder());
            vos.add(v);
        }
        return vos;
    }

    private List<ProjectVO> buildProjectVOs(List<ProjectRef> projects) {
        List<ProjectVO> vos = new ArrayList<>();
        for (ProjectRef p : projects) {
            ProjectVO v = new ProjectVO();
            v.setId(p.getId());
            v.setName(p.getName());
            v.setCode(p.getCode());
            vos.add(v);
        }
        return vos;
    }

    private MealLedger requireLedger(Long id) {
        MealLedger l = ledgerMapper.selectById(id);
        if (l == null) {
            throw BizException.of(ErrorCode.LEDGER_NOT_FOUND, "餐账不存在: " + id);
        }
        return l;
    }

    private static boolean isCancelled(String status) {
        if (status == null) {
            return false;
        }
        String s = status.toUpperCase();
        return s.contains("CANCEL") || s.contains("REFUND") || s.contains("取消") || s.contains("退款");
    }

    private static LocalDateTime parseTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(s).toLocalDateTime();
            } catch (DateTimeParseException e2) {
                throw BizException.of(ErrorCode.PARAM_ERROR, "mealTime 格式非法: " + s);
            }
        }
    }

    private static String row(String k, String v) {
        return csv(k) + "," + csv(v) + "\n";
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private record LedgerData(MealLedger ledger, List<LedgerOrder> orders,
                              Map<Long, List<OrderItem>> itemsByOrder,
                              Map<Long, List<ItemAllocation>> allocsByItem,
                              List<Participant> participants, List<ProjectRef> projects) {}
}
