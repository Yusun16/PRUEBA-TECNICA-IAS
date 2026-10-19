package com.pagoclaro.support.service;

import com.pagoclaro.support.domain.ReconciliationEngine;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.io.IOException;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationContractTest {
    @TestFactory Stream<DynamicTest> sharedScenarios() throws IOException {
        return ReconciliationContractCases.load().stream().map(test -> DynamicTest.dynamicTest(test.name(), () -> {
            var actual = new ReconciliationEngine(test.allowDuplicateCompensation()).evaluate(test.snapshot());
            var expected = test.expected();
            assertThat(actual.outcome()).isEqualTo(expected.outcome());
            assertThat(actual.reason()).isEqualTo(expected.reason());
            assertThat(actual.targetStatus()).isEqualTo(expected.status());
            assertThat(actual.netBefore()).isEqualByComparingTo(expected.netBefore());
            assertThat(actual.netAfter()).isEqualByComparingTo(expected.netAfter());
            assertThat(actual.movements()).hasSize(expected.movements());
        }));
    }
}
