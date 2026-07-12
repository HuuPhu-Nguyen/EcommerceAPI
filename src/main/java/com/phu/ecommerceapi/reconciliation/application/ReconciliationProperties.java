package com.phu.ecommerceapi.reconciliation.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.reconciliation")
public record ReconciliationProperties(
        @Min(1) @Max(10_000) int batchSize,
        @Min(1) @Max(100_000) int maxIssuesPerRun
) {
}
