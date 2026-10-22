package com.mcd.ledger.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** PennyAllocator 单测：幂等、余数派发、负数总额、Σ==total 不变式。 */
class PennyAllocatorTest {

    @Test
    void evenSplit_noRemainder() {
        List<Long> r = PennyAllocator.allocate(3900, List.of(1, 1, 1, 1));
        assertEquals(List.of(975L, 975L, 975L, 975L), r);
    }

    @Test
    void remainderDistributedToLargestRemainder_tieByIndexAsc() {
        // 2800 / 6 = 466 余 4，余数并列按下标升序 → 前 4 人各 467，后 2 人各 466
        List<Long> r = PennyAllocator.allocate(2800, List.of(1, 1, 1, 1, 1, 1));
        assertEquals(List.of(467L, 467L, 467L, 467L, 466L, 466L), r);
        assertEquals(2800L, sum(r));
    }

    @Test
    void largestRemainderMethod_weighted() {
        // 订单1 优惠 3000 按 [4475,4475,3975,3975] 分摊：794,794,706,706
        List<Long> r = PennyAllocator.allocate(3000, List.of(4475, 4475, 3975, 3975));
        assertEquals(List.of(794L, 794L, 706L, 706L), r);
        assertEquals(3000L, sum(r));
    }

    @Test
    void negativeTotal_absThenRestoreSign() {
        List<Long> r = PennyAllocator.allocate(-3000, List.of(4475, 4475, 3975, 3975));
        assertEquals(List.of(-794L, -794L, -706L, -706L), r);
        assertEquals(-3000L, sum(r));
    }

    @Test
    void idempotent_sameInputSameOutput() {
        List<Integer> w = List.of(3767, 3767, 467, 467, 466, 466);
        List<Long> a = PennyAllocator.allocate(1400, w);
        List<Long> b = PennyAllocator.allocate(1400, w);
        assertEquals(a, b);
    }

    @Test
    void zeroTotal_allZero() {
        List<Long> r = PennyAllocator.allocate(0, List.of(1, 2, 3));
        assertEquals(List.of(0L, 0L, 0L), r);
    }

    @Test
    void invariantSumEqualsTotal_propertyCheck() {
        long[] totals = {1, 7, 99, 100, 14800, 22800, 999983};
        List<List<Integer>> weightSets = List.of(
                List.of(1, 1, 1),
                List.of(3, 1, 4, 1, 5),
                List.of(4475, 4475, 3975, 3975),
                List.of(10, 20, 30, 40));
        for (long t : totals) {
            for (List<Integer> w : weightSets) {
                assertEquals(t, sum(PennyAllocator.allocate(t, w)), "total=" + t + " w=" + w);
                assertEquals(-t, sum(PennyAllocator.allocate(-t, w)), "neg total=" + t + " w=" + w);
            }
        }
    }

    @Test
    void emptyWeights_emptyResult() {
        assertTrue(PennyAllocator.allocate(100, List.of()).isEmpty());
    }

    private static long sum(List<Long> xs) {
        long s = 0;
        for (long x : xs) {
            s += x;
        }
        return s;
    }
}
