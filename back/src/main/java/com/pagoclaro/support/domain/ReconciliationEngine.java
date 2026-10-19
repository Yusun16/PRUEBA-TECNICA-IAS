package com.pagoclaro.support.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure business policy: no persistence, clock or framework dependencies. */
public final class ReconciliationEngine {
    private final boolean allowDuplicateCompensation;

    public ReconciliationEngine() { this(false); }
    public ReconciliationEngine(boolean allowDuplicateCompensation) {
        this.allowDuplicateCompensation = allowDuplicateCompensation;
    }
    public record Payment(long id, String reference, String status, BigDecimal amount, long version) {}
    public record Event(long id, String providerEventId, String status) {}
    public record Entry(long id, String type, BigDecimal amount, Long relatedId) {}
    public record Snapshot(Payment payment, List<Event> events, List<Entry> entries) {
        public Snapshot {
            events = events.stream().sorted(java.util.Comparator.comparingLong(Event::id)).toList();
            entries = entries.stream().sorted(java.util.Comparator.comparingLong(Entry::id)).toList();
        }
    }
    public record Movement(String type, BigDecimal amount, Long relatedId) {}
    public enum Outcome { RESOLVED, ALREADY_CONSISTENT, MANUAL_REVIEW }
    public record Decision(Outcome outcome, String reason, String targetStatus,
                           BigDecimal netBefore, BigDecimal netAfter, List<Movement> movements) {
        public Decision { movements = List.copyOf(movements); }
    }

    public Decision evaluate(Snapshot snapshot) {
        Payment payment = snapshot.payment();
        BigDecimal net = snapshot.entries().stream()
                .map(e -> "DEBIT".equals(e.type()) ? e.amount()
                        : "REVERSAL".equals(e.type()) ? e.amount().negate() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (payment.amount().signum() <= 0 || !Set.of("PENDING", "CONFIRMED", "REJECTED").contains(payment.status())) {
            return manual(payment, net, "INVALID_PAYMENT");
        }
        Map<Long, Entry> debits = new HashMap<>();
        Set<Long> reversed = new HashSet<>();
        boolean invalidLedger = snapshot.entries().stream().anyMatch(entry -> entry.amount().signum() <= 0
                || !Set.of("DEBIT", "REVERSAL").contains(entry.type()));
        if (invalidLedger) return manual(payment, net, "INVALID_LEDGER");
        for (Entry entry : snapshot.entries()) {
            if ("DEBIT".equals(entry.type())) {
                if (entry.relatedId() != null || entry.amount().compareTo(payment.amount()) != 0
                        || debits.put(entry.id(), entry) != null) {
                    return manual(payment, net, "AMBIGUOUS_DEBIT");
                }
            }
        }
        for (Entry entry : snapshot.entries()) {
            if ("REVERSAL".equals(entry.type())) {
                Entry original = debits.get(entry.relatedId());
                if (original == null || original.amount().compareTo(entry.amount()) != 0
                        || !reversed.add(original.id())) {
                    return manual(payment, net, "INVALID_REVERSAL");
                }
            }
        }
        Set<String> statuses = new HashSet<>();
        snapshot.events().forEach(e -> statuses.add(e.status()));
        if (statuses.isEmpty()) return manual(payment, net, "MISSING_PROVIDER_EVIDENCE");
        if (statuses.size() != 1 || !Set.of("APPROVED", "DECLINED").containsAll(statuses)) {
            return manual(payment, net, "CONTRADICTORY_PROVIDER_EVIDENCE");
        }
        if (statuses.contains("DECLINED")) {
            if (!snapshot.entries().isEmpty()) return manual(payment, net, "DECLINED_WITH_LEDGER_HISTORY");
            return decision(payment, "REJECTED", net, BigDecimal.ZERO, List.of(), "DECLINED_WITHOUT_LEDGER");
        }
        List<Entry> active = debits.values().stream().filter(e -> !reversed.contains(e.id()))
                .sorted(java.util.Comparator.comparingLong(Entry::id)).toList();
        List<Movement> movements = new ArrayList<>();
        if (active.size() > 1 && !allowDuplicateCompensation) {
            return manual(payment, net, "DUPLICATE_DEBITS_REQUIRE_REVIEW");
        }
        if (active.isEmpty()) {
            // A fully reversed history might represent a refund, not a missing posting.
            if (!snapshot.entries().isEmpty()) return manual(payment, net, "APPROVED_WITH_REVERSED_HISTORY");
            movements.add(new Movement("DEBIT", payment.amount(), null));
        } else {
            for (Entry duplicate : active.subList(1, active.size())) {
                movements.add(new Movement("REVERSAL", duplicate.amount(), duplicate.id()));
            }
        }
        return decision(payment, "CONFIRMED", net, payment.amount(), movements, "APPROVED_RECONCILED");
    }

    private Decision decision(Payment p, String status, BigDecimal before, BigDecimal after,
                              List<Movement> movements, String reason) {
        boolean unchanged = p.status().equals(status) && movements.isEmpty();
        return new Decision(unchanged ? Outcome.ALREADY_CONSISTENT : Outcome.RESOLVED,
                unchanged ? "NO_CHANGES_REQUIRED" : reason, status, before, after, movements);
    }

    private Decision manual(Payment p, BigDecimal net, String reason) {
        return new Decision(Outcome.MANUAL_REVIEW, reason, p.status(), net, net, List.of());
    }
}
