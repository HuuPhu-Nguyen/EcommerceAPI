package com.phu.ecommerceapi.audit.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.audit.hash-verification")
public record AuditHashVerificationProperties(
        @Min(1) @Max(10_000) int batchSize
) {
}
