package com.phu.ecommerceapi.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String EXTERNAL_CORRELATION_ID_ATTRIBUTE = "externalCorrelationId";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final Pattern EXTERNAL_CORRELATION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    private final List<CidrBlock> trustedProxies;

    public RequestIdFilter(
            @Value("${app.security.forwarded-headers.trusted-proxies:}") String trustedProxyCidrs
    ) {
        this.trustedProxies = parseTrustedProxyCidrs(trustedProxyCidrs);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String externalCorrelationId = resolveExternalCorrelationId(request);

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(EXTERNAL_CORRELATION_ID_ATTRIBUTE, externalCorrelationId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_ATTRIBUTE, requestId);
        MDC.put(CORRELATION_ID_MDC_KEY, externalCorrelationId == null ? requestId : externalCorrelationId);
        RequestMetadataHolder.set(new RequestMetadata(
                requestId,
                externalCorrelationId,
                resolveIpAddress(request),
                normalizeHeader(request.getHeader("User-Agent"), 500)
        ));

        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestMetadataHolder.clear();
            MDC.remove(REQUEST_ID_ATTRIBUTE);
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private String resolveExternalCorrelationId(HttpServletRequest request) {
        String candidate = request.getHeader(REQUEST_ID_HEADER);
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String normalized = candidate.trim();
        if (!EXTERNAL_CORRELATION_ID_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String remoteAddress = normalizeHeader(request.getRemoteAddr(), 100);
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            for (String candidate : forwardedFor.split(",")) {
                Optional<String> parsedAddress = parseIpLiteral(candidate);
                if (parsedAddress.isPresent()) {
                    return normalizeHeader(parsedAddress.get(), 100);
                }
            }
        }
        return remoteAddress;
    }

    private boolean isTrustedProxy(String remoteAddress) {
        Optional<byte[]> remoteAddressBytes = parseIpLiteralBytes(remoteAddress);
        return remoteAddressBytes
                .map(address -> trustedProxies.stream().anyMatch(proxy -> proxy.contains(address)))
                .orElse(false);
    }

    private String normalizeHeader(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private List<CidrBlock> parseTrustedProxyCidrs(String trustedProxyCidrs) {
        if (trustedProxyCidrs == null || trustedProxyCidrs.isBlank()) {
            return List.of();
        }

        List<CidrBlock> cidrBlocks = new ArrayList<>();
        for (String rawCidr : trustedProxyCidrs.split(",")) {
            String cidr = rawCidr.trim();
            if (!cidr.isEmpty()) {
                cidrBlocks.add(CidrBlock.parse(cidr));
            }
        }
        return List.copyOf(cidrBlocks);
    }

    private Optional<String> parseIpLiteral(String candidate) {
        return parseIpLiteralBytes(candidate).map(this::formatIpAddress);
    }

    private static Optional<byte[]> parseIpLiteralBytes(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }

        String normalized = candidate.trim();
        Optional<byte[]> ipv4Address = parseIpv4Literal(normalized);
        if (ipv4Address.isPresent()) {
            return ipv4Address;
        }
        return parseIpv6Literal(normalized);
    }

    private static Optional<byte[]> parseIpv4Literal(String candidate) {
        String[] parts = candidate.split("\\.", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }

        byte[] address = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return Optional.empty();
            }
            int octet = Integer.parseInt(part);
            if (octet > 255) {
                return Optional.empty();
            }
            address[index] = (byte) octet;
        }
        return Optional.of(address);
    }

    private static Optional<byte[]> parseIpv6Literal(String candidate) {
        if (!candidate.contains(":") || !hasOnlyIpv6LiteralCharacters(candidate)) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(candidate).getAddress());
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }

    private static boolean hasOnlyIpv6LiteralCharacters(String candidate) {
        for (int index = 0; index < candidate.length(); index++) {
            char character = candidate.charAt(index);
            if (Character.digit(character, 16) < 0 && character != ':' && character != '.') {
                return false;
            }
        }
        return true;
    }

    private String formatIpAddress(byte[] address) {
        try {
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid IP address bytes", exception);
        }
    }

    private record CidrBlock(byte[] networkAddress, int prefixBits) {

        static CidrBlock parse(String cidr) {
            String[] parts = cidr.split("/", -1);
            if (parts.length > 2 || parts[0].isBlank()) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr);
            }

            byte[] address = parseCidrAddress(cidr, parts[0].trim());
            int maxPrefixBits = address.length * 8;
            int prefixBits = parts.length == 1 ? maxPrefixBits : parsePrefixBits(cidr, parts[1], maxPrefixBits);
            return new CidrBlock(mask(address, prefixBits), prefixBits);
        }

        boolean contains(byte[] address) {
            return address.length == networkAddress.length
                    && Arrays.equals(mask(address, prefixBits), networkAddress);
        }

        private static byte[] parseCidrAddress(String cidr, String addressPart) {
            return parseIpLiteralBytes(addressPart)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr));
        }

        private static int parsePrefixBits(String cidr, String prefixPart, int maxPrefixBits) {
            try {
                int parsedPrefix = Integer.parseInt(prefixPart.trim());
                if (parsedPrefix < 0 || parsedPrefix > maxPrefixBits) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr);
                }
                return parsedPrefix;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr, exception);
            }
        }

        private static byte[] mask(byte[] address, int prefixBits) {
            byte[] masked = Arrays.copyOf(address, address.length);
            int fullBytes = prefixBits / Byte.SIZE;
            int remainingBits = prefixBits % Byte.SIZE;
            int firstZeroedByte = fullBytes;

            if (remainingBits > 0) {
                int mask = 0xFF << (Byte.SIZE - remainingBits);
                masked[fullBytes] = (byte) (masked[fullBytes] & mask);
                firstZeroedByte++;
            }

            for (int index = firstZeroedByte; index < masked.length; index++) {
                masked[index] = 0;
            }
            return masked;
        }
    }
}
