package com.pagoclaro.support.service;

import com.pagoclaro.support.domain.ReconciliationEngine.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Types;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs only against an explicitly supplied, dedicated Oracle laboratory schema. */
@Tag("oracle")
@EnabledIfEnvironmentVariable(named = "ORACLE_TEST_URL", matches = ".+")
class OracleReconciliationContractTest {
    @TestFactory Stream<DynamicTest> sharedScenariosOnOracle() throws Exception {
        return ReconciliationContractCases.load().stream().map(test -> DynamicTest.dynamicTest(test.name(), () -> verify(test)));
    }

    private void verify(ReconciliationContractCases.Case test) throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("ORACLE_TEST_URL"),
                System.getenv("ORACLE_TEST_USER"), System.getenv("ORACLE_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                // Only insert isolated fixtures; never UPDATE/DELETE pre-existing laboratory rows.
                long offset = 1_000_000_000L;
                var original = test.payment();
                var payment = new Payment(offset + original.id(), "CT-" + test.name(), original.status(), original.amount(), 0);
                var events = test.events().stream().map(e -> new Event(offset + e.id(), "CT-" + test.name() + '-' + e.id(), e.status())).toList();
                var entries = test.entries().stream().map(e -> new Entry(offset + e.id(), e.type(), e.amount(),
                        e.relatedId() == null ? null : offset + e.relatedId())).toList();
                var snapshot = new Snapshot(payment, events, entries);
                insert(connection, "INSERT INTO payment VALUES (?,?,?,?,LOCALTIMESTAMP,0)",
                        payment.id(), payment.reference(), payment.status(), payment.amount());
                for (var event : snapshot.events()) insert(connection,
                        "INSERT INTO provider_event VALUES (?,?,?,?,LOCALTIMESTAMP)", event.id(), payment.id(), event.providerEventId(), event.status());
                if (test.name().equals("partial-debit")) {
                    var entry = snapshot.entries().getFirst();
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> insert(connection,
                            "INSERT INTO ledger_entry VALUES (?,?,?,?,?,LOCALTIMESTAMP)",
                            entry.id(), payment.id(), entry.type(), entry.amount(), entry.relatedId()))
                            .isInstanceOfSatisfying(java.sql.SQLException.class, error -> assertThat(error.getErrorCode()).isEqualTo(20003));
                    assertThat(count(connection, "ledger_entry", payment.id())).isZero();
                    return; // Invalid legacy snapshots remain covered by the pure Java contract.
                }
                for (var entry : snapshot.entries()) insert(connection,
                        "INSERT INTO ledger_entry VALUES (?,?,?,?,?,LOCALTIMESTAMP)", entry.id(), payment.id(), entry.type(), entry.amount(), entry.relatedId());
                try (var call = connection.prepareCall("{call reconcile_payment(?,?,?,?,?,?)}")) {
                    call.setString(1, payment.reference());
                    call.registerOutParameter(2, Types.VARCHAR); call.registerOutParameter(3, Types.VARCHAR);
                    call.registerOutParameter(4, Types.NUMERIC); call.registerOutParameter(5, Types.NUMERIC);
                    call.setInt(6, test.allowDuplicateCompensation() ? 1 : 0);
                    call.execute();
                    var expected = test.expected();
                    assertThat(call.getString(2)).isEqualTo(expected.outcome().name());
                    assertThat(call.getString(3)).isEqualTo(expected.reason());
                    assertThat(call.getBigDecimal(4)).isEqualByComparingTo(expected.netBefore());
                    assertThat(call.getBigDecimal(5)).isEqualByComparingTo(expected.netAfter());
                    int auditCount = expected.outcome() == Outcome.ALREADY_CONSISTENT ? 0 : 1;
                    assertThat(count(connection, "ledger_entry", payment.id())).isEqualTo(entries.size() + expected.movements());
                    assertThat(count(connection, "support_audit", payment.id())).isEqualTo(auditCount);
                    try (var statement = connection.prepareStatement("SELECT status,row_version FROM payment WHERE id=?")) {
                        statement.setLong(1, payment.id());
                        try (var row = statement.executeQuery()) {
                            assertThat(row.next()).isTrue();
                            assertThat(row.getString(1)).isEqualTo(expected.status());
                            assertThat(row.getLong(2)).isEqualTo(expected.outcome() == Outcome.RESOLVED ? 1 : 0);
                        }
                    }
                    if (auditCount == 1) {
                        try (var statement = connection.prepareStatement("SELECT evidence_key FROM support_audit WHERE payment_id=?")) {
                            statement.setLong(1, payment.id());
                            try (var row = statement.executeQuery()) {
                                assertThat(row.next()).isTrue();
                                assertThat(row.getString(1)).isEqualTo(ReconciliationService.fingerprint(snapshot, expected.reason()));
                            }
                        }
                    }
                    call.execute();
                    assertThat(call.getString(2)).isEqualTo(expected.outcome() == Outcome.RESOLVED
                            ? "ALREADY_CONSISTENT" : expected.outcome().name());
                    assertThat(count(connection, "ledger_entry", payment.id())).isEqualTo(entries.size() + expected.movements());
                    assertThat(count(connection, "support_audit", payment.id())).isEqualTo(auditCount);
                }
            } finally { connection.rollback(); }
        }
    }

    private void insert(Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] == null) statement.setNull(i + 1, Types.NUMERIC);
                else statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    private int count(Connection connection, String table, long payment) throws Exception {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE payment_id=?")) {
            statement.setLong(1, payment);
            try (var row = statement.executeQuery()) { row.next(); return row.getInt(1); }
        }
    }
}
