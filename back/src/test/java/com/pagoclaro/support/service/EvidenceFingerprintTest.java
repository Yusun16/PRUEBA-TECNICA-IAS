package com.pagoclaro.support.service;

import com.pagoclaro.support.domain.ReconciliationEngine.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceFingerprintTest {
    @Test void orderAndDecimalScaleDoNotChangeFingerprint() {
        var payment = new Payment(1,"PAY-X","CONFIRMED",new BigDecimal("10.0"),0);
        var first = new Snapshot(payment, List.of(new Event(2,"E2","APPROVED"),new Event(1,"E1","APPROVED")),
                List.of(new Entry(2,"DEBIT",new BigDecimal("10.00"),null),new Entry(1,"DEBIT",new BigDecimal("10.0"),null)));
        var second = new Snapshot(new Payment(1,"PAY-X","CONFIRMED",new BigDecimal("10.00"),0),
                first.events().reversed(), first.entries().reversed());
        assertThat(ReconciliationService.fingerprint(first,"APPROVED_RECONCILED"))
                .isEqualTo(ReconciliationService.fingerprint(second,"APPROVED_RECONCILED"));
    }

    @Test void newEvidenceChangesFingerprintAndPublishedOracleVectorRemainsStable() {
        var payment = new Payment(3,"PAY-CQ22","PENDING",new BigDecimal("54000.00"),0);
        var original = new Snapshot(payment,List.of(new Event(4,"EVT-C1","APPROVED"),new Event(5,"EVT-C2","DECLINED")),List.of());
        assertThat(ReconciliationService.fingerprint(original,"CONTRADICTORY_PROVIDER_EVIDENCE"))
                .isEqualTo("d370b764524d08df9570e7724b05ca1ffa5b82f98ced2d5cc1e0e928076266af");
        var changed = new Snapshot(payment,List.of(new Event(4,"EVT-C1","APPROVED"),new Event(5,"EVT-C2","APPROVED")),List.of());
        assertThat(ReconciliationService.fingerprint(changed,"CONTRADICTORY_PROVIDER_EVIDENCE"))
                .isNotEqualTo(ReconciliationService.fingerprint(original,"CONTRADICTORY_PROVIDER_EVIDENCE"));
    }
}
