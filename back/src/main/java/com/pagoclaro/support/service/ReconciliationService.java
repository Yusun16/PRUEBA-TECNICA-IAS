package com.pagoclaro.support.service;

import com.pagoclaro.support.domain.ReconciliationEngine;
import com.pagoclaro.support.domain.ReconciliationEngine.*;
import com.pagoclaro.support.persistence.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private final PaymentRepository repository;
    private final ReconciliationEngine engine = new ReconciliationEngine();

    public ReconciliationService(PaymentRepository repository) { this.repository = repository; }

    public record Result(String reference, Outcome outcome, String reason, String paymentStatus,
                         BigDecimal netDebitBefore, BigDecimal netDebitAfter,
                         int movementsCreated, boolean auditCreated) {}

    @Transactional(timeout = 15)
    public Result reconcile(String reference) {
        Payment payment = repository.lock(reference).orElseThrow(PaymentNotFoundException::new);
        Snapshot snapshot = repository.load(payment);
        Decision decision = engine.evaluate(snapshot);
        boolean audit = false;
        if (decision.outcome() == Outcome.RESOLVED) repository.apply(payment, decision);
        if (decision.outcome() != Outcome.ALREADY_CONSISTENT) {
            audit = repository.audit(payment.id(), decision, fingerprint(snapshot, decision.reason()));
        }
        boolean auditCreated = audit;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                log.info("reconciliation committed paymentId={} outcome={} reason={} movements={} auditCreated={}",
                        payment.id(), decision.outcome(), decision.reason(), decision.movements().size(), auditCreated);
            }
        });
        return new Result(reference, decision.outcome(), decision.reason(), decision.targetStatus(),
                decision.netBefore(), decision.netAfter(), decision.movements().size(), audit);
    }

    /** Canonical evidence includes identities and statuses; timestamps are not business evidence. */
    static String fingerprint(Snapshot snapshot, String reason) {
        Payment p = snapshot.payment();
        String key = hash("policy-v1|" + reason);
        key = hash(key + "|P:" + p.id() + ':' + p.status() + ':' + p.amount().setScale(2).toPlainString() + ':' + p.version());
        for (Event e : snapshot.events()) key = hash(key + "|E:" + e.id() + ':' + e.status());
        for (Entry e : snapshot.entries()) key = hash(key + "|L:" + e.id() + ':' + e.type()
                + ':' + e.amount().setScale(2).toPlainString() + ':' + e.relatedId());
        return key;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static class PaymentNotFoundException extends RuntimeException {}
}
