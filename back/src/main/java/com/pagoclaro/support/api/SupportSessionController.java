package com.pagoclaro.support.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Verifies access without reading or modifying payment data. Authentication remains stateless. */
@RestController
@RequestMapping("/api/support/session")
public class SupportSessionController {
    public record Access(String username, String role) {}

    @GetMapping
    public ResponseEntity<Access> current(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Access(authentication.getName(), "SUPPORT"));
    }
}
