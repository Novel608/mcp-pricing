package com.mcd.ledger.engine.model;

import com.mcd.ledger.engine.AllocationType;
import java.util.List;

/**
 * 核算引擎输入（与领域实体解耦，便于纯单测；设计 §5「引擎不依赖 MCP 字段与 Web 框架」）。
 * 金额单位一律「分」（Long）。
 */
public record ReconInput(
        Long subsidyLimitCents,
        boolean includeDeliveryInSubsidy,
        List<EngineOrder> orders,
        List<EngineParticipant> participants,
        List<EngineProject> projects) {

    public record EngineProject(Long projectId, String name, String code) {}

    public record EngineOrder(
            Long orderId,
            String mcpOrderId,
            Long paidTotalCents,
            Long discountCents,
            Long deliveryFeeCents,
            List<EngineItem> items) {}

    public record EngineItem(
            Long itemId,
            String name,
            Long itemTotalCents,
            AllocationType type,
            List<EngineAlloc> allocations) {}

    public record EngineAlloc(Long participantId, int weight) {}

    public record EngineParticipant(
            Long participantId,
            String displayName,
            Long projectId,
            Integer sortOrder) {}
}
