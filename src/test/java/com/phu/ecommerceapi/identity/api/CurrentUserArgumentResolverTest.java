package com.phu.ecommerceapi.identity.api;

import com.phu.ecommerceapi.identity.application.CurrentUser;
import com.phu.ecommerceapi.identity.application.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserArgumentResolverTest {

    @Test
    void supportsAnnotatedCurrentUserParametersOnly() throws NoSuchMethodException {
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(providerReturning(currentUser()));

        assertThat(resolver.supportsParameter(parameter("withAuthenticatedUser"))).isTrue();
        assertThat(resolver.supportsParameter(parameter("withoutAnnotation"))).isFalse();
        assertThat(resolver.supportsParameter(parameter("withWrongType"))).isFalse();
    }

    @Test
    void resolvesRequiredCurrentUserFromProvider() throws Exception {
        CurrentUser currentUser = currentUser();
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(providerReturning(currentUser));

        Object resolved = resolver.resolveArgument(
                parameter("withAuthenticatedUser"),
                null,
                null,
                null
        );

        assertThat(resolved).isEqualTo(currentUser);
    }

    private MethodParameter parameter(String methodName) throws NoSuchMethodException {
        Method method = Controller.class.getDeclaredMethod(methodName, parameterType(methodName));
        return new MethodParameter(method, 0);
    }

    private Class<?> parameterType(String methodName) {
        if ("withWrongType".equals(methodName)) {
            return String.class;
        }
        return CurrentUser.class;
    }

    private CurrentUserProvider providerReturning(CurrentUser currentUser) {
        return new CurrentUserProvider() {
            @Override
            public Optional<CurrentUser> getCurrentUser() {
                return Optional.of(currentUser);
            }

            @Override
            public CurrentUser requireCurrentUser() {
                return currentUser;
            }
        };
    }

    private CurrentUser currentUser() {
        return new CurrentUser("subject-1", "customer@example.com", "customer@example.com", Set.of("customer"), Set.of());
    }

    private static class Controller {
        void withAuthenticatedUser(@AuthenticatedUser CurrentUser currentUser) {
        }

        void withoutAnnotation(CurrentUser currentUser) {
        }

        void withWrongType(@AuthenticatedUser String currentUser) {
        }
    }
}
