package com.pagoclaro.support.persistence;

import com.pagoclaro.support.domain.ReconciliationEngine.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.CannotAcquireLockException;

import java.util.List;
import java.util.Optional;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;
    public PaymentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Payment> lock(String reference) {
        try {
            return jdbc.query("""
                SELECT id, external_ref, status, amount, row_version
                FROM payment WHERE external_ref = ? FOR UPDATE WAIT 5
                """, (rs, row) -> new Payment(rs.getBigDecimal("id").longValueExact(), rs.getString("external_ref"),
                rs.getString("status"), rs.getBigDecimal("amount"), rs.getBigDecimal("row_version").longValueExact()), reference)
                .stream().findFirst();
        } catch (QueryTimeoutException ex) {
            throw new CannotAcquireLockException("Payment lock wait timed out", ex);
        }
    }

    public Snapshot load(Payment payment) {
        List<Event> events = jdbc.query("""
                SELECT id, provider_event_id, provider_status FROM provider_event
                WHERE payment_id = ? ORDER BY id
                """, (rs, row) -> new Event(rs.getBigDecimal(1).longValueExact(), rs.getString(2), rs.getString(3)), payment.id());
        List<Entry> entries = jdbc.query("""
                SELECT id, entry_type, amount, related_entry_id FROM ledger_entry
                WHERE payment_id = ? ORDER BY id
                """, (rs, row) -> new Entry(rs.getBigDecimal(1).longValueExact(), rs.getString(2), rs.getBigDecimal(3),
                rs.getObject(4) == null ? null : rs.getBigDecimal(4).longValueExact()), payment.id());
        return new Snapshot(payment, events, entries);
    }

    public void apply(Payment payment, Decision decision) {
        for (Movement movement : decision.movements()) {
            jdbc.update("""
                    INSERT INTO ledger_entry(id, payment_id, entry_type, amount, related_entry_id, created_at)
                    VALUES (ledger_entry_seq.NEXTVAL, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, payment.id(), movement.type(), movement.amount(), movement.relatedId());
        }
        int updated = jdbc.update("""
                UPDATE payment SET status = ?, updated_at = CURRENT_TIMESTAMP, row_version = row_version + 1
                WHERE id = ? AND row_version = ?
                """, decision.targetStatus(), payment.id(), payment.version());
        if (updated != 1) throw new IllegalStateException("Payment version changed while locked");
    }

    public boolean audit(long paymentId, Decision decision, String fingerprint) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM support_audit WHERE payment_id = ? AND evidence_key = ?
                """, Integer.class, paymentId, fingerprint);
        if (count != null && count > 0) return false;
        jdbc.update("""
                INSERT INTO support_audit(id, payment_id, action, reason, evidence_key, created_at)
                VALUES (support_audit_seq.NEXTVAL, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, paymentId, decision.outcome().name(), decision.reason(), fingerprint);
        return true;
    }
}
