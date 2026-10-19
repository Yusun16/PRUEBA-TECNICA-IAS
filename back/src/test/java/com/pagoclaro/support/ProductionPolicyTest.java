package com.pagoclaro.support;

import com.pagoclaro.support.domain.ReconciliationEngine.Outcome;
import com.pagoclaro.support.service.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:safe-policy;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "reconciliation.allow-duplicate-compensation=false"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class ProductionPolicyTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired ReconciliationService service;
    @Autowired JdbcTemplate jdbc;

    @Test @org.springframework.security.test.context.support.WithMockUser(roles = "SUPPORT")
    void unsafeDuplicateAssumptionIsNotEnabledByTheService() throws Exception {
        var first = service.reconcile("PAY-BX17");
        var repeated = service.reconcile("PAY-BX17");
        assertThat(first.outcome()).isEqualTo(Outcome.MANUAL_REVIEW);
        assertThat(first.reason()).isEqualTo("DUPLICATE_DEBITS_REQUIRE_REVIEW");
        assertThat(first.netDebitAfter()).isEqualByComparingTo("178000");
        assertThat(first.movementsCreated()).isZero();
        assertThat(repeated.auditCreated()).isFalse();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/support/payments/PAY-BX17/reconcile")
                .param("allowDuplicateCompensation", "true")
                .contentType("application/json").content("{\"allowDuplicateCompensation\":true}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.outcome").value("MANUAL_REVIEW"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.movementsCreated").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entry WHERE payment_id=2", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT row_version FROM payment WHERE id=2", Long.class)).isZero();
    }
}
