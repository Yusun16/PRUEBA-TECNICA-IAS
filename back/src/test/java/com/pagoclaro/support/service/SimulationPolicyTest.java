package com.pagoclaro.support.service;

import com.pagoclaro.support.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SimulationPolicyTest {
    @Test void configurationCannotAuthorizeCompensationOutsideLocalSimulation() {
        for (String[] profiles : new String[][]{{"oracle"}, {"production"}, {"local", "oracle"}}) {
            var environment = new MockEnvironment();
            environment.setActiveProfiles(profiles);
            assertThatThrownBy(() -> new ReconciliationService(mock(PaymentRepository.class), true, environment))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void safeConfigurationWorksWithoutSimulationProfile() {
        assertThatCode(() -> new ReconciliationService(mock(PaymentRepository.class), false, new MockEnvironment()))
                .doesNotThrowAnyException();
    }
}
