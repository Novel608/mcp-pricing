package com.mcd.ledger;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 麦麦餐帐 — 团队餐费对账助手（事后对账主线 F1–F5 + 选餐增量）。
 * 基包 com.mcd.ledger；依赖方向：com.mcd.plan.* 可依赖 com.mcd.ledger.*，反之严禁。
 */
@SpringBootApplication
@MapperScan({"com.mcd.ledger.mapper", "com.mcd.plan.mapper"})
@EnableCaching
public class LedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
