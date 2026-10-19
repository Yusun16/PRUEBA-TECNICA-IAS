package com.pagoclaro.support.api;

import com.pagoclaro.support.service.ReconciliationService;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/payments")
public class ReconciliationController {
    private final ReconciliationService service;
    public ReconciliationController(ReconciliationService service) { this.service = service; }

    @PostMapping("/{reference}/reconcile")
    public ReconciliationService.Result reconcile(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String reference) {
        return service.reconcile(reference);
    }
}
