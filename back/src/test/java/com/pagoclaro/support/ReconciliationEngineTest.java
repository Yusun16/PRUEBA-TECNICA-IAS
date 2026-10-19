package com.pagoclaro.support;

import com.pagoclaro.support.domain.ReconciliationEngine;
import com.pagoclaro.support.domain.ReconciliationEngine.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationEngineTest {
    private final ReconciliationEngine engine = new ReconciliationEngine();

    @Test void duplicateReversalsAreNotTreatedAsSafe() {
        assertThat(evaluate(List.of(entry(1,"DEBIT","10",null), entry(2,"REVERSAL","10",1L),
                entry(3,"REVERSAL","10",1L))).reason()).isEqualTo("INVALID_REVERSAL");
    }

    @Test void partialReversalIsNotTreatedAsSafe() {
        assertThat(evaluate(List.of(entry(1,"DEBIT","10",null), entry(2,"REVERSAL","5",1L))).reason())
                .isEqualTo("INVALID_REVERSAL");
    }

    @Test void keepsLowestActiveDebitAndIgnoresAlreadyReversedDebits() {
        var result = evaluate(List.of(entry(4,"DEBIT","10",null), entry(2,"DEBIT","10",null),
                entry(1,"DEBIT","10",null), entry(5,"REVERSAL","10",1L)));
        assertThat(result.movements()).containsExactly(new Movement("REVERSAL",new BigDecimal("10"),4L));
        assertThat(result.netAfter()).isEqualByComparingTo("10");
    }

    @Test void invalidAmountOrUnknownTypeRequiresReview() {
        assertThat(evaluate(List.of(entry(1,"DEBIT","-10",null))).reason()).isEqualTo("INVALID_LEDGER");
        assertThat(evaluate(List.of(entry(1,"UNKNOWN","10",null))).reason()).isEqualTo("INVALID_LEDGER");
    }

    private Decision evaluate(List<Entry> entries) {
        return engine.evaluate(new Snapshot(new Payment(1,"PAY-TEST","CONFIRMED",new BigDecimal("10"),0),
                List.of(new Event(1,"EVT-1","APPROVED")), entries));
    }
    private Entry entry(long id,String type,String amount,Long related) {
        return new Entry(id,type,new BigDecimal(amount),related);
    }
}
