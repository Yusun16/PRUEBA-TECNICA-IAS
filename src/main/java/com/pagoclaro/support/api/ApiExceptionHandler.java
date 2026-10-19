package com.pagoclaro.support.api;

import com.pagoclaro.support.service.ReconciliationService.PaymentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail missing() { return problem(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "No existe el pago solicitado."); }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail invalid() { return problem(HttpStatus.BAD_REQUEST, "INVALID_REFERENCE", "Referencia inválida: use entre 1 y 40 letras, números, guiones o guiones bajos."); }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ProblemDetail busy() { return problem(HttpStatus.CONFLICT, "PAYMENT_BUSY", "Pago ocupado. Reintente la operación."); }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail persistence(Exception ex) {
        log.error("reconciliation persistence failure type={}", ex.getClass().getSimpleName());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "PERSISTENCE_UNAVAILABLE", "No fue posible completar la operación. Reintente y verifique el estado.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("reconciliation unexpected failure type={}", ex.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "No fue posible completar la operación.");
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code);
        return problem;
    }
}
