package com.mcd.plan.sandbox;

import com.mcd.ledger.config.McpProperties;
import com.mcd.ledger.mcp.McpClient;
import com.mcd.ledger.mcp.McpRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * McpClient Bean 装配（设计 §7.5「只替换最底层数据源」）：
 *   mcd.sandbox.enabled=false → 真实 HTTP McpClient；
 *   mcd.sandbox.enabled=true（默认）→ SandboxMcpClient 沙盒顶替（不触网、无需 Token）。
 * 恰好一个 McpClient Bean；适配层零 if(isSandbox)。
 *
 * 置于 plan 包：plan 可依赖 ledger（com.mcd.ledger.*），避免 ledger→plan 反向依赖（设计 §7.5 ArchUnit）。
 */
@Configuration
public class McpClientConfig {

    @Bean
    @ConditionalOnProperty(name = "mcd.sandbox.enabled", havingValue = "false")
    public McpClient httpMcpClient(McpProperties props, McpRateLimiter rateLimiter) {
        return new McpClient(props, rateLimiter);
    }

    @Bean
    @ConditionalOnProperty(name = "mcd.sandbox.enabled", havingValue = "true", matchIfMissing = true)
    public McpClient sandboxMcpClient(McpProperties props, McpRateLimiter rateLimiter,
                                      SandboxPlanFixture fixture) {
        return new SandboxMcpClient(props, rateLimiter, fixture);
    }
}
