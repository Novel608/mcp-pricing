package com.mcd.ledger.engine;

import com.mcd.ledger.engine.model.ReconInput;
import com.mcd.ledger.engine.model.ReconInput.EngineAlloc;
import com.mcd.ledger.engine.model.ReconInput.EngineItem;
import com.mcd.ledger.engine.model.ReconInput.EngineOrder;
import com.mcd.ledger.engine.model.ReconInput.EngineParticipant;
import com.mcd.ledger.engine.model.ReconInput.EngineProject;
import java.util.List;

/**
 * 沙盒基准数据集（设计 §9，两单·六人·两项目，单位：分）。
 * 引擎单测固定用例；与 data-sandbox.sql 同源。
 *
 * 注意：项目归属以设计 §9.3/§9.4 数值口径为准——
 *   项目A（核心账务重构）= 林/陈/王/孙；项目B（AI助手接入）= 赵/周。
 * （任务书散文写作「赵→A、孙→B」与目标数字 15716/7084 矛盾，按数值口径实现。）
 */
final class SandboxFixtures {

    // 参与人 id
    static final long LIN = 1, CHEN = 2, WANG = 3, ZHAO = 4, SUN = 5, ZHOU = 6;
    // 项目 id
    static final long PROJ_A = 1, PROJ_B = 2;

    static final long SUBSIDY_LIMIT = 3500;

    private SandboxFixtures() {}

    static ReconInput build() {
        List<EngineParticipant> participants = List.of(
                new EngineParticipant(LIN, "林工", PROJ_A, 0),
                new EngineParticipant(CHEN, "陈工", PROJ_A, 1),
                new EngineParticipant(WANG, "王工", PROJ_A, 2),
                new EngineParticipant(ZHAO, "赵工", PROJ_B, 3),
                new EngineParticipant(SUN, "孙工", PROJ_A, 4),
                new EngineParticipant(ZHOU, "周工", PROJ_B, 5));

        List<EngineProject> projects = List.of(
                new EngineProject(PROJ_A, "核心账务重构", "PROJ-CORE"),
                new EngineProject(PROJ_B, "AI助手接入", "PROJ-AI"));

        // 订单1：麦乐送，实付 14800，优惠 -3000，配送 +900
        List<EngineItem> order1Items = List.of(
                personal(101, "巨无霸套餐", 3500, LIN),
                personal(102, "巨无霸套餐", 3500, CHEN),
                personal(103, "双层吉士堡套餐", 3000, WANG),
                personal(104, "双层吉士堡套餐", 3000, ZHAO),
                shared(105, "麦乐鸡20块", 3900, List.of(LIN, CHEN, WANG, ZHAO)));
        EngineOrder order1 = new EngineOrder(1L, "MCD-20261022-01", 14800L, -3000L, 900L, order1Items);

        // 订单2：到店自提，实付 8000，优惠 -1400，配送 0
        // 薯条共享顺序固定为 孙/周/陈/林/王/赵 → 467×4 + 466×2
        List<EngineItem> order2Items = List.of(
                personal(201, "板烧鸡腿堡套餐", 3300, SUN),
                personal(202, "板烧鸡腿堡套餐", 3300, ZHOU),
                shared(203, "霸王薯条大份", 2800, List.of(SUN, ZHOU, CHEN, LIN, WANG, ZHAO)));
        EngineOrder order2 = new EngineOrder(2L, "MCD-20261022-02", 8000L, -1400L, 0L, order2Items);

        return new ReconInput(SUBSIDY_LIMIT, false, List.of(order1, order2), participants, projects);
    }

    private static EngineItem personal(long itemId, String name, long total, long participantId) {
        return new EngineItem(itemId, name, total, AllocationType.PERSONAL,
                List.of(new EngineAlloc(participantId, 1)));
    }

    private static EngineItem shared(long itemId, String name, long total, List<Long> participantIds) {
        List<EngineAlloc> allocs = participantIds.stream()
                .map(pid -> new EngineAlloc(pid, 1))
                .toList();
        return new EngineItem(itemId, name, total, AllocationType.SHARED, allocs);
    }
}
