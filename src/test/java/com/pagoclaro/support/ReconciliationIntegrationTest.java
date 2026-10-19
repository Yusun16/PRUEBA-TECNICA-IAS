package com.pagoclaro.support;

import com.pagoclaro.support.domain.ReconciliationEngine.Outcome;
import com.pagoclaro.support.persistence.PaymentRepository;
import com.pagoclaro.support.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationIntegrationTest {
    @Autowired ReconciliationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoSpyBean PaymentRepository repository;

    @BeforeEach
    void resetData() {
        reset(repository);
        jdbc.update("DELETE FROM support_audit");
        jdbc.update("DELETE FROM ledger_entry WHERE entry_type = 'REVERSAL'");
        jdbc.update("DELETE FROM ledger_entry");
        jdbc.update("DELETE FROM provider_event");
        jdbc.update("DELETE FROM payment");
        payment(1, "PAY-AX91", "PENDING", "125000");
        payment(2, "PAY-BX17", "CONFIRMED", "89000");
        payment(3, "PAY-CQ22", "PENDING", "54000");
        payment(4, "PAY-DK44", "CONFIRMED", "31000");
        event(1, 1, "APPROVED"); event(2, 2, "APPROVED"); event(3, 2, "APPROVED");
        event(4, 3, "APPROVED"); event(5, 3, "DECLINED"); event(6, 4, "APPROVED");
        debit(5001, 1, "125000"); debit(5002, 2, "89000");
        debit(5003, 2, "89000"); debit(5004, 4, "31000");
    }

    @Test void fixesStatusWithoutPostingAgain() {
        var result = service.reconcile("PAY-AX91");
        assertThat(result.outcome()).isEqualTo(Outcome.RESOLVED);
        assertThat(result.paymentStatus()).isEqualTo("CONFIRMED");
        assertThat(result.movementsCreated()).isZero();
        assertThat(count("ledger_entry", 1)).isEqualTo(1);
        assertThat(version(1)).isEqualTo(1);
        assertThat(service.reconcile("PAY-AX91").outcome()).isEqualTo(Outcome.ALREADY_CONSISTENT);
        assertThat(count("support_audit", 1)).isEqualTo(1);
        assertThat(version(1)).isEqualTo(1);
    }

    @Test void reversesDuplicateAndPreservesOriginalEntriesAcrossRetries() {
        var result = service.reconcile("PAY-BX17");
        assertThat(result.netDebitBefore()).isEqualByComparingTo("178000");
        assertThat(result.netDebitAfter()).isEqualByComparingTo("89000");
        assertThat(result.movementsCreated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT related_entry_id FROM ledger_entry WHERE entry_type='REVERSAL'", Long.class)).isEqualTo(5003L);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM ledger_entry WHERE id IN (5002,5003)", BigDecimal.class)).isEqualByComparingTo("178000");
        for (int i = 0; i < 3; i++) assertThat(service.reconcile("PAY-BX17").outcome()).isEqualTo(Outcome.ALREADY_CONSISTENT);
        assertThat(count("ledger_entry", 2)).isEqualTo(3);
        assertThat(count("support_audit", 2)).isEqualTo(1);
    }

    @Test void contradictionIsManualAndAuditIsIdempotent() {
        var first = service.reconcile("PAY-CQ22");
        var second = service.reconcile("PAY-CQ22");
        assertThat(first.outcome()).isEqualTo(Outcome.MANUAL_REVIEW);
        assertThat(first.auditCreated()).isTrue();
        assertThat(jdbc.queryForObject("SELECT evidence_key FROM support_audit WHERE payment_id=3", String.class))
                .isEqualTo("d370b764524d08df9570e7724b05ca1ffa5b82f98ced2d5cc1e0e928076266af");
        assertThat(second.auditCreated()).isFalse();
        assertThat(second.paymentStatus()).isEqualTo("PENDING");
        assertThat(count("ledger_entry", 3)).isZero();
        assertThat(count("support_audit", 3)).isEqualTo(1);
        assertThat(version(3)).isZero();
        event(7, 3, "APPROVED");
        assertThat(service.reconcile("PAY-CQ22").auditCreated()).isTrue();
        assertThat(count("support_audit", 3)).isEqualTo(2);
    }

    @Test void healthyPaymentHasNoSideEffects() {
        assertThat(service.reconcile("PAY-DK44").outcome()).isEqualTo(Outcome.ALREADY_CONSISTENT);
        assertThat(count("ledger_entry", 4)).isEqualTo(1);
        assertThat(count("support_audit", 4)).isZero();
        assertThat(version(4)).isZero();
    }

    @Test void missingEvidenceAndMissingPaymentAreHandled() {
        payment(5, "PAY-EMPTY", "PENDING", "10.50");
        assertThat(service.reconcile("PAY-EMPTY").reason()).isEqualTo("MISSING_PROVIDER_EVIDENCE");
        assertThatThrownBy(() -> service.reconcile("MISSING")).isInstanceOf(ReconciliationService.PaymentNotFoundException.class);
    }

    @Test void createsMissingDebitUsingDecimalPrecision() {
        payment(5, "PAY-NEW", "PENDING", "10.51"); event(7, 5, "APPROVED");
        assertThat(service.reconcile("PAY-NEW").netDebitAfter()).isEqualByComparingTo("10.51");
        service.reconcile("PAY-NEW");
        assertThat(count("ledger_entry", 5)).isEqualTo(1);
        assertThat(count("support_audit", 5)).isEqualTo(1);
    }

    @Test void declinedWithoutHistoryIsRejectedButWithDebitNeedsReview() {
        payment(5, "PAY-DECLINED", "PENDING", "10"); event(7, 5, "DECLINED");
        assertThat(service.reconcile("PAY-DECLINED").paymentStatus()).isEqualTo("REJECTED");
        assertThat(service.reconcile("PAY-DECLINED").outcome()).isEqualTo(Outcome.ALREADY_CONSISTENT);
        debit(5005, 5, "10");
        assertThat(service.reconcile("PAY-DECLINED").outcome()).isEqualTo(Outcome.MANUAL_REVIEW);
        assertThat(count("ledger_entry", 5)).isEqualTo(1);
    }

    @Test void partialDebitRequiresManualReview() {
        jdbc.update("UPDATE ledger_entry SET amount=1 WHERE id=5003");
        assertThat(service.reconcile("PAY-BX17").reason()).isEqualTo("AMBIGUOUS_DEBIT");
        assertThat(count("ledger_entry", 2)).isEqualTo(2);
        assertThat(version(2)).isZero();
    }

    @Test void fullyReversedApprovalDoesNotRecreateDebit() {
        jdbc.update("INSERT INTO ledger_entry VALUES (6000,4,'REVERSAL',31000,5004,CURRENT_TIMESTAMP)");
        assertThat(service.reconcile("PAY-DK44").reason()).isEqualTo("APPROVED_WITH_REVERSED_HISTORY");
        assertThat(count("ledger_entry", 4)).isEqualTo(2);
    }

    @Test void crossPaymentReversalRequiresReview() {
        jdbc.update("INSERT INTO ledger_entry VALUES (6000,2,'REVERSAL',125000,5001,CURRENT_TIMESTAMP)");
        assertThat(service.reconcile("PAY-BX17").reason()).isEqualTo("INVALID_REVERSAL");
        assertThat(count("ledger_entry", 2)).isEqualTo(3);
    }

    @Test void concurrentRequestsProduceOneReversalAndOneAudit() throws Exception {
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { gate.await(); return service.reconcile("PAY-BX17"); });
            var two = executor.submit(() -> { gate.await(); return service.reconcile("PAY-BX17"); });
            gate.countDown();
            assertThat(java.util.List.of(one.get(10, TimeUnit.SECONDS).outcome(), two.get(10, TimeUnit.SECONDS).outcome()))
                    .containsExactlyInAnyOrder(Outcome.RESOLVED, Outcome.ALREADY_CONSISTENT);
        }
        assertThat(count("ledger_entry", 2)).isEqualTo(3);
        assertThat(count("support_audit", 2)).isEqualTo(1);
        assertThat(version(2)).isEqualTo(1);
    }

    @Test void auditFailureRollsBackLedgerAndPayment() {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("injected audit failure"))
                .when(repository).audit(eq(2L), any(), anyString());
        assertThatThrownBy(() -> service.reconcile("PAY-BX17"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("ledger_entry", 2)).isEqualTo(2);
        assertThat(count("support_audit", 2)).isZero();
        assertThat(version(2)).isZero();
    }

    @Test void auditFailureAlsoRollsBackStatusCorrection() {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("injected audit failure"))
                .when(repository).audit(eq(1L), any(), anyString());
        assertThatThrownBy(() -> service.reconcile("PAY-AX91"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE id=1", String.class)).isEqualTo("PENDING");
        assertThat(version(1)).isZero();
        assertThat(count("support_audit", 1)).isZero();
    }

    @Test @WithMockUser(roles = "SUPPORT")
    void persistenceFailureIsSanitizedAndRollsBack() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("secret JDBC credentials SQL"))
                .when(repository).audit(eq(2L), any(), anyString());
        mvc.perform(post("/api/support/payments/PAY-BX17/reconcile"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("PERSISTENCE_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        assertThat(count("ledger_entry", 2)).isEqualTo(2);
        assertThat(version(2)).isZero();
    }

    @Test void anonymousRequestIsRejected() throws Exception {
        mvc.perform(post("/api/support/payments/PAY-BX17/reconcile")).andExpect(status().isUnauthorized());
        assertThat(count("ledger_entry", 2)).isEqualTo(2);
    }

    @Test @WithMockUser(roles = "SUPPORT")
    void apiReturnsBusinessResultAndSafeErrors() throws Exception {
        mvc.perform(post("/api/support/payments/PAY-BX17/reconcile"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("RESOLVED"))
                .andExpect(jsonPath("$.movementsCreated").value(1));
        mvc.perform(post("/api/support/payments/NO-SUCH-PAYMENT/reconcile"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("PAYMENT_NOT_FOUND"));
        mvc.perform(post("/api/support/payments/bad.reference/reconcile"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("INVALID_REFERENCE"));
        mvc.perform(post("/api/support/payments/PAY-CQ22/reconcile"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("MANUAL_REVIEW"));
    }

    @Test @WithMockUser(roles = "VIEWER")
    void wrongRoleCannotReconcile() throws Exception {
        mvc.perform(post("/api/support/payments/PAY-BX17/reconcile")).andExpect(status().isForbidden());
    }

    private void payment(int id, String reference, String status, String amount) {
        jdbc.update("INSERT INTO payment VALUES (?,?,?,?,CURRENT_TIMESTAMP,0)", id, reference, status, new BigDecimal(amount));
    }
    private void event(int id, int payment, String status) {
        jdbc.update("INSERT INTO provider_event VALUES (?,?,?,?,CURRENT_TIMESTAMP)", id, payment, "EVT-" + id, status);
    }
    private void debit(int id, int payment, String amount) {
        jdbc.update("INSERT INTO ledger_entry VALUES (?,?,'DEBIT',?,NULL,CURRENT_TIMESTAMP)", id, payment, new BigDecimal(amount));
    }
    private int count(String table, int payment) {
        // Table names are test constants, never user input.
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE payment_id=?", Integer.class, payment);
    }
    private long version(int payment) {
        return jdbc.queryForObject("SELECT row_version FROM payment WHERE id=?", Long.class, payment);
    }
}
