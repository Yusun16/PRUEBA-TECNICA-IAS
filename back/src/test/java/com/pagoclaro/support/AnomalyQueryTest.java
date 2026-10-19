package com.pagoclaro.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

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
        jdbc.update("UPDATE payment SET updated_at=LOCALTIMESTAMP - INTERVAL '2' DAY");
        jdbc.update("UPDATE provider_event SET received_at=LOCALTIMESTAMP - INTERVAL '2' DAY");
        jdbc.update("UPDATE ledger_entry SET created_at=LOCALTIMESTAMP - INTERVAL '2' DAY");
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
}
