package com.pagoclaro.support.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

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
    @Operation(summary = "Validar acceso de soporte",
            description = "Valida credenciales y rol sin modificar pagos. No genera token ni sesión; cada petición requiere HTTP Basic.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Acceso válido"),
        @ApiResponse(responseCode = "401", description = "Credenciales ausentes o inválidas", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Requiere rol SUPPORT", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    })
    public ResponseEntity<Access> current(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Access(authentication.getName(), "SUPPORT"));
    }
}
