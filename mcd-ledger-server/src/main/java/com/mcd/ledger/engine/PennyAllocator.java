package com.mcd.ledger.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 最大余额法分摊器（设计 §4.3，P1 精确到分）。
 *
 * 规则：
 *  1. 每人精确值 = totalCents × weight / Σweight，取整数商与余数；
 *  2. leftover = totalCents − Σ商，按余数从大到小派发；
 *  3. 余数相同按下标升序派发，保证幂等（相同输入永远相同输出）；
 *  4. 支持负数总额：先取绝对值分摊，再统一还原符号；
 *  5. 不变式：Σ result == totalCents；全程 long 整数运算，无浮点。
 */
public final class PennyAllocator {

    private PennyAllocator() {}

    public static List<Long> allocate(long totalCents, List<Integer> weights) {
        int n = weights.size();
        List<Long> result = new ArrayList<>(n);
        if (n == 0) {
            return result;
        }

        long sumW = 0L;
        for (int w : weights) {
            sumW += w;
        }
        if (sumW == 0L) {
            // 权重全 0：无法按权重分摊，全部记 0（尾差由上层恒等校验捕获）
            for (int i = 0; i < n; i++) {
                result.add(0L);
            }
            return result;
        }

        boolean negative = totalCents < 0;
        long total = Math.abs(totalCents);

        long[] quotient = new long[n];
        long[] remainder = new long[n];
        long sumQuotient = 0L;
        for (int i = 0; i < n; i++) {
            long prod = total * (long) weights.get(i);
            quotient[i] = prod / sumW;   // 非负数的整数除法即 floor
            remainder[i] = prod % sumW;
            sumQuotient += quotient[i];
        }

        long leftover = total - sumQuotient; // 0 <= leftover < n（数学保证）

        // 派发顺序：余数降序，平局按下标升序（确定性 → 幂等）
        List<Integer> order = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort((a, b) -> {
            if (remainder[a] != remainder[b]) {
                return Long.compare(remainder[b], remainder[a]);
            }
            return Integer.compare(a, b);
        });

        for (long k = 0; k < leftover; k++) {
            quotient[order.get((int) (k % n))] += 1;
        }

        for (int i = 0; i < n; i++) {
            result.add(negative ? -quotient[i] : quotient[i]);
        }
        return result;
    }
}
