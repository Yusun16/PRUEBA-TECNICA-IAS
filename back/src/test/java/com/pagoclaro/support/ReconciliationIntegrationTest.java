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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reconciliation-test;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.security.user.name=n3", "spring.security.user.password=demo-change-me"})
@AutoConfigureMockMvc
class ReconciliationIntegrationTest {
    @Autowired ReconciliationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoSpyBean PaymentRepository repository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void loginValidatesCredentialsWithoutChangingPayments() throws Exception {
        mvc.perform(get("/api/support/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/support/session").with(httpBasic("n3", "incorrect")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/support/session").with(httpBasic("n3", "demo-change-me")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("n3"))
                .andExpect(jsonPath("$.role").value("SUPPORT"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(count("support_audit", 2)).isZero();
        assertThat(count("ledger_entry", 2)).isEqualTo(2);
        assertThat(version(2)).isZero();
    }

    @Test @WithMockUser(roles = "VIEWER")
    void loginRequiresSupportRole() throws Exception {
        mvc.perform(get("/api/support/session")).andExpect(status().isForbidden());
    }

    @BeforeEach
    void resetData() {
        reset(repository);
        // Dedicated, disposable test database: never mutate historical rows to reset fixtures.
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(new ClassPathResource("db/local/schema.sql"),
                new ClassPathResource("db/local/data.sql")).execute(jdbc.getDataSource());
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

    @Test void approvalWithoutCaptureNeverCreatesDebit() {
        payment(5, "PAY-NEW", "PENDING", "10.51"); event(7, 5, "APPROVED");
        assertThat(service.reconcile("PAY-NEW").reason()).isEqualTo("APPROVED_WITHOUT_CAPTURE_EVIDENCE");
        assertThat(version(5)).isZero();
        service.reconcile("PAY-NEW");
        assertThat(count("ledger_entry", 5)).isZero();
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

    @Test void incompatibleNewDebitIsRejectedAndLegacyPartialDebitRequiresReview() {
        payment(5, "PAY-PARTIAL", "PENDING", "10"); event(7, 5, "APPROVED");
        assertThatThrownBy(() -> debit(5005, 5, "1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("ledger_entry", 5)).isZero();
        // Isolated test only: emulate an invalid legacy row predating the guard.
        jdbc.execute("DROP TRIGGER trg_ledger_guard");
        debit(5005, 5, "1");
        assertThat(service.reconcile("PAY-PARTIAL").reason()).isEqualTo("AMBIGUOUS_DEBIT");
        assertThat(count("ledger_entry", 5)).isEqualTo(1);
        assertThat(version(5)).isZero();
    }

    @Test void multiRowInvalidDebitRollsBackWholeStatement() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ledger_entry VALUES
                  (8000,2,'DEBIT',89000,NULL,CURRENT_TIMESTAMP),
                  (8001,2,'DEBIT',1,NULL,CURRENT_TIMESTAMP)
                """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("ledger_entry", 2)).isEqualTo(2);
        jdbc.update("""
                INSERT INTO ledger_entry VALUES
                  (8000,2,'DEBIT',89000,NULL,CURRENT_TIMESTAMP),
                  (8001,2,'REVERSAL',89000,8000,CURRENT_TIMESTAMP)
                """);
        assertThat(count("ledger_entry", 2)).isEqualTo(4);
    }

    @Test void fullyReversedApprovalDoesNotRecreateDebit() {
        jdbc.update("INSERT INTO ledger_entry VALUES (6000,4,'REVERSAL',31000,5004,CURRENT_TIMESTAMP)");
        assertThat(service.reconcile("PAY-DK44").reason()).isEqualTo("APPROVED_WITH_REVERSED_HISTORY");
        assertThat(count("ledger_entry", 4)).isEqualTo(2);
    }

    @Test void databaseRejectsCrossPaymentAndPartialReversals() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ledger_entry VALUES (6000,2,'REVERSAL',125000,5001,CURRENT_TIMESTAMP)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ledger_entry VALUES (6000,2,'REVERSAL',1,5003,CURRENT_TIMESTAMP)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("ledger_entry", 2)).isEqualTo(2);
    }

    @Test void databaseEnforcesImmutabilityAndOneReversalPerDebit() {
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entry SET amount=1 WHERE id=5003"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entry WHERE id=5003"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO ledger_entry VALUES (6000,2,'REVERSAL',89000,5003,CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ledger_entry VALUES (6001,2,'REVERSAL',89000,5003,CURRENT_TIMESTAMP)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ledger_entry VALUES (6001,2,'REVERSAL',89000,6000,CURRENT_TIMESTAMP)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("ledger_entry", 2)).isEqualTo(3);
    }

    @Test void directEventWriterWaitsForParentAndNewEvidenceGetsOneNewAudit() throws Exception {
        var started = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> future = new java.util.concurrent.atomic.AtomicReference<>();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                repository.lock("PAY-CQ22").orElseThrow();
                future.set(executor.submit(() -> { started.countDown(); event(7, 3, "APPROVED"); }));
                try {
                    assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> future.get().get(200, TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                } catch (InterruptedException ex) { throw new RuntimeException(ex); }
                assertThat(service.reconcile("PAY-CQ22").auditCreated()).isTrue();
            });
            future.get().get(5, TimeUnit.SECONDS);
        }
        assertThat(service.reconcile("PAY-CQ22").auditCreated()).isTrue();
        assertThat(service.reconcile("PAY-CQ22").auditCreated()).isFalse();
        assertThat(count("support_audit", 3)).isEqualTo(2);
    }

    @Test void competingDirectReversalCannotBypassParentOrDuplicateCompensation() throws Exception {
        var started = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> future = new java.util.concurrent.atomic.AtomicReference<>();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                repository.lock("PAY-BX17").orElseThrow();
                future.set(executor.submit(() -> {
                    started.countDown();
                    jdbc.update("INSERT INTO ledger_entry VALUES (7000,2,'REVERSAL',89000,5003,CURRENT_TIMESTAMP)");
                }));
                try {
                    assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> future.get().get(200, TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                } catch (InterruptedException ex) { throw new RuntimeException(ex); }
                assertThat(service.reconcile("PAY-BX17").movementsCreated()).isEqualTo(1);
            });
            assertThatThrownBy(() -> future.get().get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThat(count("ledger_entry", 2)).isEqualTo(3);
        assertThat(count("support_audit", 2)).isEqualTo(1);
    }

    @Test @WithMockUser(roles = "SUPPORT")
    void lockTimeoutReturnsConflictWithoutPartialWritesAndRetrySucceeds() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                repository.lock("PAY-BX17").orElseThrow(); locked.countDown();
                try { if (!release.await(12, TimeUnit.SECONDS)) throw new IllegalStateException("Lock test timed out"); }
                catch (InterruptedException ex) { throw new RuntimeException(ex); }
            }));
            try {
                assertThat(locked.await(2, TimeUnit.SECONDS)).isTrue();
                mvc.perform(post("/api/support/payments/PAY-BX17/reconcile"))
                        .andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("PAYMENT_BUSY"));
                assertThat(count("ledger_entry", 2)).isEqualTo(2);
                assertThat(count("support_audit", 2)).isZero();
                assertThat(version(2)).isZero();
            } finally { release.countDown(); }
            holder.get(5, TimeUnit.SECONDS);
        }
        assertThat(service.reconcile("PAY-BX17").outcome()).isEqualTo(Outcome.RESOLVED);
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

    @Test void secondReversalInsertFailureRollsBackTheFirstInsertAndRetryIsSafe() {
        payment(5, "PAY-MULTIPLE", "PENDING", "10"); event(7, 5, "APPROVED");
        debit(9000, 5, "10"); debit(9001, 5, "10"); debit(9002, 5, "10");
        // First sequence value (10000) succeeds, second (10001) hits a real PK collision.
        debit(10001, 4, "31000");
        assertThatThrownBy(() -> service.reconcile("PAY-MULTIPLE"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("ledger_entry", 5)).isEqualTo(3);
        assertThat(count("support_audit", 5)).isZero();
        assertThat(version(5)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE id=5", String.class)).isEqualTo("PENDING");
        var retry = service.reconcile("PAY-MULTIPLE");
        assertThat(retry.movementsCreated()).isEqualTo(2);
        assertThat(retry.netDebitAfter()).isEqualByComparingTo("10");
        assertThat(count("support_audit", 5)).isEqualTo(1);
        assertThat(service.reconcile("PAY-MULTIPLE").outcome()).isEqualTo(Outcome.ALREADY_CONSISTENT);
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

    @Test @WithMockUser(roles = "SUPPORT")
    void unsupportedNumericIdentifierFailsWithoutTruncatingIntoAnotherPayment() throws Exception {
        jdbc.update("INSERT INTO payment VALUES (5.5,'PAY-BAD-ID','PENDING',10,CURRENT_TIMESTAMP,0)");
        mvc.perform(post("/api/support/payments/PAY-BAD-ID/reconcile"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.title").value("INTERNAL_ERROR"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_audit", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entry", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT id FROM payment WHERE external_ref='PAY-BAD-ID'", BigDecimal.class))
                .isEqualByComparingTo("5.5");
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
