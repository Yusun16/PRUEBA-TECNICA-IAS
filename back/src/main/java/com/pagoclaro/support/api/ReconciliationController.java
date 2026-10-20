package com.pagoclaro.support.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;

import com.pagoclaro.support.service.ReconciliationService;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/payments")
public class ReconciliationController {
    private final ReconciliationService service;
    public ReconciliationController(ReconciliationService service) { this.service = service; }

    @PostMapping("/{reference}/reconcile")
    @Operation(summary = "Conciliar un pago",
            description = "Sin cuerpo. Evalúa la evidencia transaccional; puede actualizar el estado, auditar y crear compensaciones. Repetir no debe duplicar movimientos.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "RESOLVED, ALREADY_CONSISTENT o MANUAL_REVIEW"),
        @ApiResponse(responseCode = "400", description = "Referencia inválida", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Credenciales ausentes o inválidas", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Requiere rol SUPPORT", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Pago inexistente", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Pago ocupado", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "503", description = "Persistencia no disponible", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    })
    public ReconciliationService.Result reconcile(
            @Parameter(description = "Entre 1 y 40 letras ASCII, números, guiones o guiones bajos", example = "PAY-AX91")
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String reference) {
        return service.reconcile(reference);
    }
}
