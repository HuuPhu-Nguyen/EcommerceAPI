package com.phu.ecommerceapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openapi")
public record OpenApiExposureProperties(
        boolean publicDocsEnabled
) {
}
