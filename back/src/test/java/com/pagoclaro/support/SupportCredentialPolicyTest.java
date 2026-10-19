package com.pagoclaro.support;

import com.pagoclaro.support.api.SupportCredentialPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class SupportCredentialPolicyTest {
    @Test void rejectsDemoOrMissingPasswordInEveryNonLocalProfile() {
        for (String[] profiles : new String[][]{{"oracle"}, {"production"}, {"local", "oracle"}}) {
            var environment = new MockEnvironment();
            environment.setActiveProfiles(profiles);
            for (String password : new String[]{"demo-change-me", "", " "}) {
                assertThatThrownBy(() -> new SupportCredentialPolicy(environment, password))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }
    @Test void acceptsExplicitNonDemoPasswordOutsideLocalAndDemoInLocal() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("oracle");
        assertThatCode(() -> new SupportCredentialPolicy(environment, "test-only-distinct-password"))
                .doesNotThrowAnyException();
        environment.setActiveProfiles("local");
        assertThatCode(() -> new SupportCredentialPolicy(environment, "demo-change-me"))
                .doesNotThrowAnyException();
    }
}
