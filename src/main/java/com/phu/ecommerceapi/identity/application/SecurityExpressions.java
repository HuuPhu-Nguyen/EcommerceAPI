package com.phu.ecommerceapi.identity.application;

public final class SecurityExpressions {

    public static final String CUSTOMER_PROFILE_READ =
            "hasRole('CUSTOMER') and hasAuthority('SCOPE_profile:read')";
    public static final String CUSTOMER_CART_READ =
            "hasRole('CUSTOMER') and hasAuthority('SCOPE_cart:read')";
    public static final String CUSTOMER_CART_WRITE =
            "hasRole('CUSTOMER') and hasAuthority('SCOPE_cart:write')";
    public static final String CUSTOMER_CHECKOUT_CREATE =
            "hasRole('CUSTOMER') and hasAuthority('SCOPE_checkout:write')";
    public static final String CUSTOMER_PAYMENT_CREATE =
            "hasRole('CUSTOMER') and hasAuthority('SCOPE_payment:create')";
    public static final String CUSTOMER_PAYMENT_REFUND =
            "hasRole('CUSTOMER') and hasAuthority('SCOPE_payment:refund')";
    public static final String ADMIN_PRODUCT_WRITE =
            "hasRole('ADMIN') and hasAuthority('SCOPE_product:write')";
    public static final String ADMIN_OR_AUDITOR_USER_READ =
            "hasAnyRole('ADMIN', 'AUDITOR') and hasAuthority('SCOPE_user:read')";
    public static final String ADMIN_OR_AUDITOR_AUDIT_READ =
            "hasAnyRole('ADMIN', 'AUDITOR') and hasAuthority('SCOPE_audit:read')";

    private SecurityExpressions() {
    }
}
