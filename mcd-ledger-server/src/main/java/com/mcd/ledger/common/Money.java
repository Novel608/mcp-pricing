package com.mcd.ledger.common;

import java.math.BigDecimal;

/**
 * 金额格式化（设计 §7.1）。全链路以「分」为 Long 存储与计算；
 * 本类是唯一允许出现「元」字符串的出口。
 */
public final class Money {

    private static final String YUAN = "\u00A5"; // ¥

    private Money() {}

    /** 14800 -> "¥148.00" */
    public static String formatYuan(long cents) {
        long abs = Math.abs(cents);
        long yuan = abs / 100;
        long frac = abs % 100;
        String sign = cents < 0 ? "-" : "";
        return String.format("%s%s%d.%02d", sign, YUAN, yuan, frac);
    }

    /** "+¥12.00" / "−¥8.00"（差异展示，负号用 U+2212 减号，正号显式） */
    public static String formatSignedYuan(long cents) {
        if (cents > 0) {
            return "+" + formatYuan(cents);
        }
        if (cents < 0) {
            // formatYuan 已带 ASCII '-'，替换为排版减号 −
            return formatYuan(cents).replace("-", "\u2212");
        }
        return formatYuan(0);
    }

    /**
     * "148.00" / "¥148.00" / "148" -> 14800（Long 分）。
     * 预留入口：MCP 若以「元小数」返回金额，统一经此转换。
     */
    public static long parseYuanToCents(String yuan) {
        if (yuan == null || yuan.isBlank()) {
            throw new IllegalArgumentException("empty yuan string");
        }
        String s = yuan.trim().replace(YUAN, "").replace(",", "").replace("\u2212", "-");
        BigDecimal bd = new BigDecimal(s);
        return bd.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }
}
