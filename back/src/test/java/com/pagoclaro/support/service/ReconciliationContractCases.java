package com.pagoclaro.support.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pagoclaro.support.domain.ReconciliationEngine.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

final class ReconciliationContractCases {
    record Expected(Outcome outcome, String reason, String status, BigDecimal netBefore,
                    BigDecimal netAfter, int movements) {}
    record Case(String name, Payment payment, List<Event> events, List<Entry> entries,
                boolean allowDuplicateCompensation, Expected expected) {
        Snapshot snapshot() { return new Snapshot(payment, events, entries); }
    }

    static List<Case> load() throws IOException {
        try (var input = ReconciliationContractCases.class.getResourceAsStream("/contract/reconciliation-cases.json")) {
            if (input == null) throw new IOException("Missing reconciliation contract fixture");
            return new ObjectMapper().readValue(input, new TypeReference<>() {});
        }
    }
}
