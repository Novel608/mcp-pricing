package com.mcd.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP 配置（application.yml: mcd.mcp.*）。
 * token 从环境变量 MCD_MCP_TOKEN 读取，缺省空串；沙盒模式无需 Token。
 */
@Component
@ConfigurationProperties(prefix = "mcd.mcp")
public class McpProperties {

    /** MCP Server 地址，默认 https://mcp.mcd.cn */
    private String serverUrl = "https://mcp.mcd.cn";

    /** Bearer Token，来自环境变量 MCD_MCP_TOKEN，缺省空串 */
    private String token = "";

    /** 令牌桶速率：次/分钟，默认 600 */
    private int rateLimitPerMin = 600;

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public int getRateLimitPerMin() { return rateLimitPerMin; }
    public void setRateLimitPerMin(int rateLimitPerMin) { this.rateLimitPerMin = rateLimitPerMin; }
}
