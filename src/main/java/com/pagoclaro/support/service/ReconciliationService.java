package com.pagoclaro.support.service;

import com.pagoclaro.support.domain.ReconciliationEngine;
import com.pagoclaro.support.domain.ReconciliationEngine.*;
import com.pagoclaro.support.persistence.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        log.info("reconciliation paymentId={} outcome={} reason={} movements={} auditCreated={}",
                payment.id(), decision.outcome(), decision.reason(), decision.movements().size(), audit);
        return new Result(reference, decision.outcome(), decision.reason(), decision.targetStatus(),
                decision.netBefore(), decision.netAfter(), decision.movements().size(), audit);
    }

    /** Canonical evidence includes identities and statuses; timestamps are not business evidence. */
    static String fingerprint(Snapshot snapshot, String reason) {
        StringBuilder data = new StringBuilder("policy-v1|").append(reason).append('|');
        Payment p = snapshot.payment();
        data.append(p.id()).append('|').append(p.status()).append('|')
                .append(p.amount().toPlainString()).append('|').append(p.version());
        snapshot.events().forEach(e -> data.append("|E:").append(e.id()).append(':').append(e.status()));
        snapshot.entries().forEach(e -> data.append("|L:").append(e.id()).append(':').append(e.type())
                .append(':').append(e.amount().toPlainString()).append(':').append(e.relatedId()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static class PaymentNotFoundException extends RuntimeException {}
}
