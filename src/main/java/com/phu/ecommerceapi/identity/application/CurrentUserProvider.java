package com.phu.ecommerceapi.identity.application;

import java.util.Optional;

public interface CurrentUserProvider {

    Optional<CurrentUser> getCurrentUser();

    CurrentUser requireCurrentUser();
}
