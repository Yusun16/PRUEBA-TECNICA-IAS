package com.pagoclaro.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Executes the actual delivery SQL; Oracle compilation still requires Oracle. */
class AnomalyQueryTest {
    JdbcTemplate jdbc;

    @BeforeEach void database() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:querytest;MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(new ClassPathResource("db/local/schema.sql"),
                new ClassPathResource("db/local/data.sql")).execute(source);
    }

    @Test void findsSampleAnomaliesWithoutMultiplyingLedgerByEvents() throws Exception {
        var rows = jdbc.queryForList(sql());
        assertThat(rows).extracting(r -> r.get("EXTERNAL_REF"))
                .containsExactly("PAY-AX91", "PAY-BX17", "PAY-CQ22");
        assertThat((java.math.BigDecimal) rows.get(1).get("NET_DEBIT")).isEqualByComparingTo("178000");
    }

    @Test void includesOldPaymentWithNewEvidenceAndCountsOldLedgerHistory() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(new ClassPathResource("db/local/schema.sql")).execute(jdbc.getDataSource());
        String oldFixtures = new ClassPathResource("db/local/data.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("CURRENT_TIMESTAMP", "LOCALTIMESTAMP - INTERVAL '2' DAY");
        new ResourceDatabasePopulator(new ByteArrayResource(oldFixtures.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .execute(jdbc.getDataSource());
        assertThat(jdbc.queryForList(sql())).isEmpty();
        jdbc.update("UPDATE provider_event SET received_at=LOCALTIMESTAMP WHERE id=3");
        var rows = jdbc.queryForList(sql());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("EXTERNAL_REF")).isEqualTo("PAY-BX17");
        assertThat((java.math.BigDecimal) rows.getFirst().get("NET_DEBIT")).isEqualByComparingTo("178000");
    }

    private String sql() throws Exception {
        return Files.readString(Path.of("db/oracle/02_detect_anomalies.sql"));
    }

    @Test void legacyInvalidReversalIsDetectedEvenWhenNetMatches() throws Exception {
        // Simulates rows present before migration, in this disposable test database only.
        jdbc.execute("DROP TRIGGER trg_ledger_guard");
        jdbc.update("INSERT INTO ledger_entry VALUES (6000,4,'DEBIT',125000,NULL,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ledger_entry VALUES (6001,4,'REVERSAL',125000,5001,CURRENT_TIMESTAMP)");
        var rows = jdbc.queryForList(sql());
        assertThat(rows).filteredOn(row -> "PAY-DK44".equals(row.get("EXTERNAL_REF")))
                .singleElement().satisfies(row -> {
                    assertThat(row.get("DIAGNOSTIC")).isEqualTo("INVALID_EVIDENCE");
                    assertThat((java.math.BigDecimal) row.get("NET_DEBIT")).isEqualByComparingTo("31000");
                });
    }

    @Test void missingEvidenceAndDeclinedStatusMismatchAreReported() throws Exception {
        jdbc.update("INSERT INTO payment VALUES (5,'PAY-NONE','PENDING',10,CURRENT_TIMESTAMP,0)");
        jdbc.update("INSERT INTO payment VALUES (6,'PAY-DECLINED','PENDING',10,CURRENT_TIMESTAMP,0)");
        jdbc.update("INSERT INTO provider_event VALUES (7,6,'E7','DECLINED',CURRENT_TIMESTAMP)");
        var rows = jdbc.queryForList(sql());
        assertThat(rows).filteredOn(row -> "PAY-NONE".equals(row.get("EXTERNAL_REF")))
                .singleElement().satisfies(row -> assertThat(row.get("DIAGNOSTIC")).isEqualTo("MISSING_PROVIDER_EVIDENCE"));
        assertThat(rows).filteredOn(row -> "PAY-DECLINED".equals(row.get("EXTERNAL_REF")))
                .singleElement().satisfies(row -> assertThat(row.get("DIAGNOSTIC")).isEqualTo("STATUS_MISMATCH"));
    }
}
