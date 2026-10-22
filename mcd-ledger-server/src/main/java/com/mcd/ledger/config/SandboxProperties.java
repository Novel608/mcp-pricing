package com.mcd.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 沙盒开关（application.yml: mcd.sandbox.enabled）。 */
@Component
@ConfigurationProperties(prefix = "mcd.sandbox")
public class SandboxProperties {

    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
