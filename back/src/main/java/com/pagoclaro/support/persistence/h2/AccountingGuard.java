package com.pagoclaro.support.persistence.h2;

import org.h2.api.Trigger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.TreeSet;

/** H2-only database adapter. Every child writer serializes on PAYMENT, even direct SQL. */
public final class AccountingGuard implements Trigger {
    private boolean ledger;

    @Override
    public void init(Connection connection, String schema, String trigger, String table, boolean before, int type) {
        ledger = "LEDGER_ENTRY".equalsIgnoreCase(table);
    }

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        if (ledger && oldRow != null) {
            throw new SQLException("Ledger entries are immutable; append a linked reversal", "23513", 20001);
        }
        var payments = new TreeSet<BigDecimal>();
        if (oldRow != null) payments.add(new BigDecimal(oldRow[1].toString()));
        if (newRow != null) payments.add(new BigDecimal(newRow[1].toString()));
        for (BigDecimal payment : payments) {
            try (var statement = connection.prepareStatement("SELECT id FROM payment WHERE id = ? FOR UPDATE WAIT 5")) {
                statement.setBigDecimal(1, payment);
                try (var row = statement.executeQuery()) {
                    if (!row.next()) throw new SQLException("Payment does not exist", "23506");
                }
            }
        }
        if (ledger && newRow != null && "REVERSAL".equals(newRow[2])) {
            try (var statement = connection.prepareStatement("""
                    SELECT COUNT(*) FROM ledger_entry
                    WHERE id = ? AND payment_id = ? AND entry_type = 'DEBIT'
                      AND amount = ? AND related_entry_id IS NULL
                    """)) {
                statement.setObject(1, newRow[4]);
                statement.setObject(2, newRow[1]);
                statement.setObject(3, newRow[3]);
                try (var row = statement.executeQuery()) {
                    row.next();
                    if (row.getInt(1) != 1) throw new SQLException("Invalid full reversal relation", "23513", 20002);
                }
            }
        }
    }
}
