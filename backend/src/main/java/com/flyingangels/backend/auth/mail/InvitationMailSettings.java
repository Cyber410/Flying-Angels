package com.flyingangels.backend.auth.mail;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component @ConfigurationProperties("app.auth.mail-outbox") @Validated @Getter @Setter
public class InvitationMailSettings {
    private boolean enabled = true;
    @Min(1) @Max(100) private int batchSize = 20;
    @Min(1) @Max(20) private int maxAttempts = 5;
    @Min(1) @Max(3600) private long retryBaseSeconds = 60;
    @Min(1) @Max(86400) private long retryMaxSeconds = 3600;
    @Min(30) @Max(3600) private long leaseSeconds = 120;
}
