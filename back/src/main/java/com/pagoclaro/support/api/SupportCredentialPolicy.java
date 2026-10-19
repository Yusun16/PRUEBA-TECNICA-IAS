package com.pagoclaro.support.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Prevent the documented laboratory password from becoming a non-local credential. */
@Component
public final class SupportCredentialPolicy {
    public SupportCredentialPolicy(Environment environment,
            @Value("${spring.security.user.password:}") String password) {
        boolean localOnly = environment.acceptsProfiles(Profiles.of("local & !oracle"));
        if (!localOnly && (password.isBlank() || "demo-change-me".equals(password))) {
            throw new IllegalStateException("Non-local support access requires an explicit non-demo password");
        }
    }
}
