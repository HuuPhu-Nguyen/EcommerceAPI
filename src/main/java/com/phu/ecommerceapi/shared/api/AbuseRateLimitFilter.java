package com.phu.ecommerceapi.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AbuseRateLimitFilter extends OncePerRequestFilter {

    private static final String PAYMENT_GROUP = "payment-create";
    private static final String REFUND_GROUP = "refund-create";
    private static final String WEBHOOK_GROUP = "provider-webhook";
    private static final String PROFILE_GROUP = "customer-profile";

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;
    private final long windowSeconds;
    private final int sensitiveRequestLimit;
    private final int webhookRequestLimit;
    private final int profileRequestLimit;
    private final int maxCounterKeys;
    private final int webhookMaxBodyBytes;
    private final int jsonApiMaxBodyBytes;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Object counterMaintenanceMonitor = new Object();

    @Autowired
    public AbuseRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.security.rate-limit.sensitive-requests-per-window:30}") int sensitiveRequestLimit,
            @Value("${app.security.rate-limit.webhook-requests-per-window:120}") int webhookRequestLimit,
            @Value("${app.security.rate-limit.profile-requests-per-window:60}") int profileRequestLimit,
            @Value("${app.security.rate-limit.max-keys:10000}") int maxCounterKeys,
            @Value("${app.security.webhook.max-body-bytes:65536}") long webhookMaxBodyBytes,
            @Value("${app.security.request.max-json-body-bytes:32768}") long jsonApiMaxBodyBytes
    ) {
        this(objectMapper, Clock.systemUTC(), enabled, windowSeconds, sensitiveRequestLimit,
                webhookRequestLimit, profileRequestLimit, maxCounterKeys, webhookMaxBodyBytes, jsonApiMaxBodyBytes);
    }

    AbuseRateLimitFilter(
            ObjectMapper objectMapper,
            Clock clock,
            boolean enabled,
            long windowSeconds,
            int sensitiveRequestLimit,
            int webhookRequestLimit,
            int profileRequestLimit,
            int maxCounterKeys,
            long webhookMaxBodyBytes,
            long jsonApiMaxBodyBytes
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.enabled = enabled;
        this.windowSeconds = Math.max(1, windowSeconds);
        this.sensitiveRequestLimit = Math.max(1, sensitiveRequestLimit);
        this.webhookRequestLimit = Math.max(1, webhookRequestLimit);
        this.profileRequestLimit = Math.max(1, profileRequestLimit);
        this.maxCounterKeys = Math.max(1, maxCounterKeys);
        this.webhookMaxBodyBytes = normalizedMaxBodyBytes(webhookMaxBodyBytes, "webhook max body bytes");
        this.jsonApiMaxBodyBytes = normalizedMaxBodyBytes(jsonApiMaxBodyBytes, "JSON API max body bytes");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String normalizedPath = normalizePath(request.getRequestURI());
        HttpServletRequest filteredRequest = enforceBodyLimit(request, response, normalizedPath);
        if (filteredRequest == null) {
            return;
        }

        RateLimitRule rule = ruleFor(filteredRequest, normalizedPath);
        if (rule == null || allow(rule, clientKey(filteredRequest, rule))) {
            filterChain.doFilter(filteredRequest, response);
            return;
        }

        writeRateLimitedResponse(filteredRequest, response);
    }

    private boolean allow(RateLimitRule rule, String clientKey) {
        long now = clock.instant().getEpochSecond();
        WindowCounter counter = counterFor(clientKey, now);
        if (counter == null) {
            return false;
        }
        synchronized (counter) {
            if (now - counter.windowStartedAt >= windowSeconds) {
                counter.windowStartedAt = now;
                counter.count = 0;
            }
            counter.lastSeenAt = now;
            if (counter.count >= rule.limit()) {
                return false;
            }
            counter.count++;
            return true;
        }
    }

    private WindowCounter counterFor(String clientKey, long now) {
        WindowCounter existingCounter = counters.get(clientKey);
        if (existingCounter != null) {
            return existingCounter;
        }

        synchronized (counterMaintenanceMonitor) {
            existingCounter = counters.get(clientKey);
            if (existingCounter != null) {
                return existingCounter;
            }

            evictExpiredCounters(now);
            if (counters.size() >= maxCounterKeys) {
                return null;
            }

            WindowCounter newCounter = new WindowCounter(now);
            counters.put(clientKey, newCounter);
            return newCounter;
        }
    }

    private void evictExpiredCounters(long now) {
        counters.entrySet().removeIf(entry -> now - entry.getValue().lastSeenAt >= windowSeconds);
    }

    private RateLimitRule ruleFor(HttpServletRequest request, String path) {
        if (!enabled) {
            return null;
        }
        String method = request.getMethod();
        if ("POST".equals(method) && "/payments".equals(path)) {
            return new RateLimitRule(PAYMENT_GROUP, sensitiveRequestLimit);
        }
        if ("POST".equals(method) && isRefundPath(path)) {
            return new RateLimitRule(REFUND_GROUP, sensitiveRequestLimit);
        }
        if ("POST".equals(method) && isProviderWebhookPath(path)) {
            return new RateLimitRule(WEBHOOK_GROUP, webhookRequestLimit);
        }
        if (("GET".equals(method) || "POST".equals(method)) && "/customer/profile/me".equals(path)) {
            return new RateLimitRule(PROFILE_GROUP, profileRequestLimit);
        }
        return null;
    }

    private HttpServletRequest enforceBodyLimit(
            HttpServletRequest request,
            HttpServletResponse response,
            String path
    ) throws IOException {
        BodyLimit bodyLimit = bodyLimitFor(request, path);
        if (bodyLimit == null) {
            return request;
        }
        if (request.getContentLengthLong() > bodyLimit.maxBodyBytes()) {
            writePayloadTooLargeResponse(request, response, bodyLimit.detail());
            return null;
        }

        byte[] requestBody = readBody(request, bodyLimit.maxBodyBytes());
        if (requestBody.length > bodyLimit.maxBodyBytes()) {
            writePayloadTooLargeResponse(request, response, bodyLimit.detail());
            return null;
        }

        return new CachedBodyHttpServletRequest(request, requestBody);
    }

    private BodyLimit bodyLimitFor(HttpServletRequest request, String path) {
        if (isProviderWebhookPost(request, path)) {
            return new BodyLimit(webhookMaxBodyBytes, "Webhook request body is too large");
        }
        if (isJsonApiWriteRequest(request, path)) {
            return new BodyLimit(jsonApiMaxBodyBytes, "JSON request body is too large");
        }
        return null;
    }

    private byte[] readBody(HttpServletRequest request, int maxBodyBytes) throws IOException {
        int readLimit = maxBodyBytes + 1;
        ByteArrayOutputStream body = new ByteArrayOutputStream(Math.min(maxBodyBytes, 8192));
        byte[] buffer = new byte[Math.min(readLimit, 8192)];
        int remaining = readLimit;
        ServletInputStream inputStream = request.getInputStream();
        while (remaining > 0) {
            int bytesRead = inputStream.read(buffer, 0, Math.min(buffer.length, remaining));
            if (bytesRead == -1) {
                break;
            }
            body.write(buffer, 0, bytesRead);
            remaining -= bytesRead;
        }
        return body.toByteArray();
    }

    private boolean isProviderWebhookPost(HttpServletRequest request, String path) {
        return "POST".equals(request.getMethod()) && isProviderWebhookPath(path);
    }

    private boolean isJsonApiWriteRequest(HttpServletRequest request, String path) {
        String method = request.getMethod();
        return ("POST".equals(method) && "/payments".equals(path))
                || ("POST".equals(method) && isRefundPath(path))
                || ("POST".equals(method) && "/checkout".equals(path))
                || isAdminProductWriteRequest(method, path)
                || isCartItemWriteRequest(method, path);
    }

    private boolean isAdminProductWriteRequest(String method, String path) {
        return ("POST".equals(method) && "/admin/products".equals(path))
                || ("PUT".equals(method) && path.startsWith("/admin/products/"));
    }

    private boolean isCartItemWriteRequest(String method, String path) {
        String[] segments = path.split("/");
        return ("POST".equals(method) && segments.length == 4
                        && "cart".equals(segments[1])
                        && "items".equals(segments[3]))
                || ("PUT".equals(method) && segments.length == 5
                        && "cart".equals(segments[1])
                        && "items".equals(segments[3]));
    }

    private boolean isRefundPath(String path) {
        return path.startsWith("/payments/") && path.endsWith("/refunds");
    }

    private boolean isProviderWebhookPath(String path) {
        return "/payments/provider-webhooks/fake".equals(path)
                || "/payments/provider-webhooks/stripe".equals(path);
    }

    private String clientKey(HttpServletRequest request, RateLimitRule rule) {
        return rule.group() + ":" + clientAddress(request);
    }

    private String clientAddress(HttpServletRequest request) {
        String trustedMetadataAddress = RequestMetadataHolder.current().ipAddress();
        if (!"unknown".equals(trustedMetadataAddress)) {
            return trustedMetadataAddress;
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
    }

    private void writeRateLimitedResponse(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests for this sensitive endpoint"
        );
        problem.setTitle("Rate limited");
        problem.setType(URI.create("urn:problem:rate-limited"));
        problem.setProperty("code", ApiErrorCode.RATE_LIMITED.name());
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("requestId", requestId(request));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(windowSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private void writePayloadTooLargeResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            String detail
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                detail
        );
        problem.setTitle("Payload too large");
        problem.setType(URI.create("urn:problem:payload-too-large"));
        problem.setProperty("code", ApiErrorCode.VALIDATION_FAILED.name());
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("requestId", requestId(request));

        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String value && !value.isBlank()) {
            return value;
        }
        return "unknown";
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private int normalizedMaxBodyBytes(long value, String propertyName) {
        long normalized = Math.max(1, value);
        if (normalized >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException(propertyName + " must be less than " + Integer.MAX_VALUE);
        }
        return (int) normalized;
    }

    private record BodyLimit(int maxBodyBytes, String detail) {
    }

    private record RateLimitRule(String group, int limit) {
    }

    private static final class WindowCounter {
        private long windowStartedAt;
        private long lastSeenAt;
        private int count;

        private WindowCounter(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
            this.lastSeenAt = windowStartedAt;
        }
    }
}
