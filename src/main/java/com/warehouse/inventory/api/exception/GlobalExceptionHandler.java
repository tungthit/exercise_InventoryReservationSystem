package com.warehouse.inventory.api.exception;

import com.warehouse.inventory.domain.exception.InsufficientStockException;
import com.warehouse.inventory.domain.exception.InvalidStateTransitionException;
import com.warehouse.inventory.domain.exception.ProductNotFoundException;
import com.warehouse.inventory.domain.exception.ReservationNotFoundException;
import com.warehouse.inventory.infrastructure.lock.LockAcquisitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── 400 ──────────────────────────────────────────────────────────────────

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ErrorResponse> handleValidation(WebExchangeBindException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Mono.just(error("VALIDATION_ERROR", details));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ErrorResponse> handleInvalidTransition(InvalidStateTransitionException ex) {
        return Mono.just(error("INVALID_STATE_TRANSITION", ex.getMessage()));
    }

    // ─── 404 ──────────────────────────────────────────────────────────────────

    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<ErrorResponse> handleNotFound(ReservationNotFoundException ex) {
        return Mono.just(error("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<ErrorResponse> handleProductNotFound(ProductNotFoundException ex) {
        return Mono.just(error("PRODUCT_NOT_FOUND", ex.getMessage()));
    }

    // ─── 409 ──────────────────────────────────────────────────────────────────

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Mono<ErrorResponse> handleInsufficientStock(InsufficientStockException ex) {
        return Mono.just(error("INSUFFICIENT_STOCK", ex.getMessage()));
    }

    @ExceptionHandler(LockAcquisitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Mono<ErrorResponse> handleLockConflict(LockAcquisitionException ex) {
        return Mono.just(error("LOCK_CONFLICT", "System is busy, please retry: " + ex.getMessage()));
    }

    // ─── 500 ──────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return Mono.just(error("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private ErrorResponse error(String code, String message) {
        return new ErrorResponse(code, message, Instant.now());
    }

    public record ErrorResponse(String code, String message, Instant timestamp) {}
}
