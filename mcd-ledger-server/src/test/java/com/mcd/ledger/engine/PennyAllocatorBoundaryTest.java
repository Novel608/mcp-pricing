package com.mcd.ledger.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QA 补测（严过关）：PennyAllocator 边界用例（设计 §4.3 未覆盖场景）。
 * 覆盖：单人权重、权重悬殊 999:1、整除平局、total=0、负数带余数、
 * 混合 0 权重、权重全 0 的既定降级、公平性（逼近精确值 <1 分；等权极差 <=1）与幂等性。
 */
class PennyAllocatorBoundaryTest {

    private static long sum(List<Long> xs) {
        long s = 0L;
        for (long x : xs) {
            s += x;
        }
        return s;
    }

    @Test
    @DisplayName("单人权重：全额归一人")
    void singleWeight_getsAll() {
        assertEquals(List.of(9999L), PennyAllocator.allocate(9999, List.of(1)));
        assertEquals(List.of(-9999L), PennyAllocator.allocate(-9999, List.of(1)));
        assertEquals(List.of(0L), PennyAllocator.allocate(0, List.of(1)));
    }

    @Test
    @DisplayName("权重悬殊 999:1 仍保持比例与不变式")
    void extremeWeightDisparity_999_to_1() {
        List<Long> r = PennyAllocator.allocate(100000, List.of(999, 1));
        assertEquals(List.of(99900L, 100L), r);
        assertEquals(100000L, sum(r));
        // 负向对称
        List<Long> neg = PennyAllocator.allocate(-100000, List.of(999, 1));
        assertEquals(List.of(-99900L, -100L), neg);
        assertEquals(-100000L, sum(neg));
    }

    @Test
    @DisplayName("所有余数相等导致平局：按下标升序派发（1000/3 -> 334,333,333）")
    void allRemaindersEqual_tieByIndexAsc() {
        List<Long> r = PennyAllocator.allocate(1000, List.of(1, 1, 1));
        assertEquals(List.of(334L, 333L, 333L), r);
        assertEquals(1000L, sum(r));
    }

    @Test
    @DisplayName("负总额带余数：先绝对值分摊再统一还原符号")
    void negativeTotal_withRemainder() {
        List<Long> r = PennyAllocator.allocate(-1000, List.of(1, 1, 1));
        assertEquals(List.of(-334L, -333L, -333L), r);
        assertEquals(-1000L, sum(r));
        // 与正向结果逐项取反对称
        List<Long> pos = PennyAllocator.allocate(1000, List.of(1, 1, 1));
        for (int i = 0; i < pos.size(); i++) {
            assertEquals(-pos.get(i), r.get(i));
        }
    }

    @Test
    @DisplayName("total=0：任何权重组合全为 0")
    void zeroTotal_anyWeights_allZero() {
        assertEquals(List.of(0L, 0L, 0L), PennyAllocator.allocate(0, List.of(1, 2, 3)));
        assertEquals(List.of(0L, 0L), PennyAllocator.allocate(0, List.of(5, 5)));
    }

    @Test
    @DisplayName("公平性：每个份额与精确值偏差严格小于 1 分（|r·Σw − total·w| < Σw）")
    void fairness_eachShareWithinOneCentOfExact() {
        long[] totals = {1, 2, 3, 7, 99, 100, 999, 14800, 22800, 999983};
        List<List<Integer>> weightSets = List.of(
                List.of(1, 1, 1),
                List.of(1, 1, 1, 1, 1, 1),
                List.of(3, 1, 4, 1, 5),
                List.of(4475, 4475, 3975, 3975),
                List.of(999, 1),
                List.of(1, 2, 3, 4, 5, 6, 7));
        for (long t : totals) {
            for (List<Integer> w : weightSets) {
                List<Long> r = PennyAllocator.allocate(t, w);
                assertEquals(t, sum(r), "Σ != total: t=" + t + " w=" + w);
                long sumW = w.stream().mapToLong(Integer::longValue).sum();
                for (int i = 0; i < r.size(); i++) {
                    assertTrue(r.get(i) >= 0, "非负总额不应出现负份额: t=" + t + " w=" + w + " r=" + r);
                    long deviation = Math.abs(r.get(i) * sumW - t * w.get(i));
                    assertTrue(deviation < sumW,
                            "份额应逼近精确值(偏差<1分): t=" + t + " w=" + w + " i=" + i + " r=" + r);
                }
            }
        }
    }

    @Test
    @DisplayName("等权特例：极差不超过 1 分（最大余额法直观性质）")
    void equalWeights_maxMinusMinAtMostOne() {
        long[] totals = {1, 2, 3, 7, 99, 100, 999, 14800, 22800, 999983};
        for (long t : totals) {
            List<Long> r = PennyAllocator.allocate(t, List.of(1, 1, 1, 1, 1, 1));
            assertEquals(t, sum(r));
            long max = r.stream().mapToLong(Long::longValue).max().orElseThrow();
            long min = r.stream().mapToLong(Long::longValue).min().orElseThrow();
            assertTrue(max - min <= 1, "等权时极差应<=1: t=" + t + " r=" + r);
        }
    }

    @Test
    @DisplayName("混合 0 权重：0 权重者得 0，其余按比例")
    void mixedZeroWeights() {
        List<Long> r = PennyAllocator.allocate(500, List.of(0, 1));
        assertEquals(List.of(0L, 500L), r);
        assertEquals(500L, sum(r));
    }

    @Test
    @DisplayName("权重全 0：既定降级为全 0（尾差交由上层恒等校验捕获，不静默造数）")
    void allZeroWeights_documentedDegradation() {
        List<Long> r = PennyAllocator.allocate(777, List.of(0, 0, 0));
        assertEquals(List.of(0L, 0L, 0L), r);
        // 注意：此场景 Σ != total，上层 ReconciliationEngine 必须能暴露（diff!=0 阻断导出）
    }

    @Test
    @DisplayName("幂等：同输入（含负数）永远同输出")
    void idempotent_includingNegative() {
        List<Integer> w = List.of(7, 11, 13, 17);
        for (long t : new long[]{12345, -12345, 1, -1, 0}) {
            assertEquals(PennyAllocator.allocate(t, w), PennyAllocator.allocate(t, w),
                    "t=" + t);
        }
    }

    @Test
    @DisplayName("空权重：空结果（不抛异常）")
    void emptyWeights() {
        assertTrue(PennyAllocator.allocate(100, List.of()).isEmpty());
    }

    @Test
    @DisplayName("订单2 薯条场景复现：2800 分 6 人 -> 467x4 + 466x2")
    void sandboxFries_2800_sixPeople() {
        List<Long> r = PennyAllocator.allocate(2800, List.of(1, 1, 1, 1, 1, 1));
        assertEquals(List.of(467L, 467L, 467L, 467L, 466L, 466L), r);
        assertEquals(2800L, sum(r));
    }

    @Test
    @DisplayName("负数总额平局同样按下标升序（与正向对称）")
    void negativeTie_symmetricToPositive() {
        List<Long> pos = PennyAllocator.allocate(2800, List.of(1, 1, 1, 1, 1, 1));
        List<Long> neg = PennyAllocator.allocate(-2800, List.of(1, 1, 1, 1, 1, 1));
        for (int i = 0; i < pos.size(); i++) {
            assertEquals(-pos.get(i), neg.get(i));
        }
    }

    @Test
    @DisplayName("极端越界输入 Long.MIN_VALUE：当前实现不保证不变式（已知边界，仅登记行为）")
    void longMinValue_knownEdge_documentOnly() {
        // Math.abs(Long.MIN_VALUE) 溢出为负值。业务上金额不可能达到该量级（约 92 亿元），
        // 此处仅固定现有行为，防止未来改动意外改变：不抛未受检异常、结果长度为 n。
        List<Long> r = PennyAllocator.allocate(Long.MIN_VALUE, List.of(1, 1));
        assertEquals(2, r.size());
    }
}
