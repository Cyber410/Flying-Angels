package com.flyingangels.backend.auth.throttle;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component @ConfigurationProperties("app.security.login-throttle") @Validated @Getter @Setter
public class LoginThrottleSettings {
    private boolean enabled = true;
    private boolean cleanupEnabled = true;
    @Min(1) @Max(1000) private int accountMaxFailures = 5;
    @Min(1) @Max(10000) private int ipMaxFailures = 100;
    @Min(1) @Max(86400) private long windowSeconds = 900;
    @Min(1) @Max(86400) private long blockSeconds = 900;
    @Min(1) @Max(1000) private int cleanupBatchSize = 200;
    private String trustedProxies = "";
}
