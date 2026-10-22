package com.mcd.ledger.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QA 补测（严过关）：Money.parseYuanToCents 金额单位转换。
 * 输入分两类：① 寇豆码文档样例（"22"/"33.5"/"40"）；② A1 真实响应实测格式
 * （.a1-probe/08-query-meal-detail.json 的 diffPrice 全部为 "+ ¥0"/"+ ¥3"/"- ¥0.5" 形态）。
 * 修复前 "+ ¥0" 因「+ 号后带空格」被 BigDecimal 拒绝并吞异常返回 null，选餐域加价解析全废。
 */
class MoneyTest {

    @Test
    @DisplayName("文档样例：元字符串 -> 分")
    void docSamples() {
        assertEquals(2200L, Money.parseYuanToCents("22"));
        assertEquals(3350L, Money.parseYuanToCents("33.5"));
        assertEquals(4000L, Money.parseYuanToCents("40"));
        assertEquals(14800L, Money.parseYuanToCents("148.00"));
        assertEquals(14800L, Money.parseYuanToCents("¥148.00"));
        assertEquals(148000L, Money.parseYuanToCents("1,480")); // 1480 元 = 148000 分
    }

    @Test
    @DisplayName("A1 实测格式：符号+空格+币种（'+ ¥3' -> 300；'- ¥0.5' -> -50）")
    void a1RealFormats_diffPrice() {
        assertEquals(0L, Money.parseYuanToCents("+ ¥0"));
        assertEquals(50L, Money.parseYuanToCents("+ ¥0.5"));
        assertEquals(100L, Money.parseYuanToCents("+ ¥1"));
        assertEquals(300L, Money.parseYuanToCents("¥3"));
        assertEquals(350L, Money.parseYuanToCents("+ ¥3.5"));
        assertEquals(900L, Money.parseYuanToCents("+ ¥9"));
        assertEquals(1100L, Money.parseYuanToCents("+ ¥11"));
        assertEquals(-50L, Money.parseYuanToCents("- ¥0.5"));
        assertEquals(-100L, Money.parseYuanToCents("-¥1"));
        // 排版减号（U+2212）兼容
        assertEquals(-50L, Money.parseYuanToCents("\u2212¥0.5"));
    }

    @Test
    @DisplayName("非法输入抛 IllegalArgumentException（不静默吞掉）")
    void invalidInputs_throw() {
        assertThrows(IllegalArgumentException.class, () -> Money.parseYuanToCents(null));
        assertThrows(IllegalArgumentException.class, () -> Money.parseYuanToCents("   "));
        assertThrows(IllegalArgumentException.class, () -> Money.parseYuanToCents("¥"));
        assertThrows(IllegalArgumentException.class, () -> Money.parseYuanToCents("+"));
        assertThrows(IllegalArgumentException.class, () -> Money.parseYuanToCents("abc"));
    }

    @Test
    @DisplayName("往返一致性：formatYuan(parseYuanToCents(x)) 金额守恒")
    void roundTrip() {
        for (String raw : new String[]{"22", "33.5", "40", "148.00", "+ ¥3", "- ¥0.5", "¥0"}) {
            long cents = Money.parseYuanToCents(raw);
            assertEquals(cents, Money.parseYuanToCents(Money.formatYuan(cents)),
                    "round-trip failed for " + raw);
        }
    }
}
