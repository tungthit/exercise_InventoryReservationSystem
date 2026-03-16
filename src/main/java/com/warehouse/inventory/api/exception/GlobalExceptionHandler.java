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


import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── 400 ──────────────────────────────────────────────────────────────────

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error("VALIDATION_ERROR", details);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidTransition(InvalidStateTransitionException ex) {
        return error("INVALID_STATE_TRANSITION", ex.getMessage());
    }

    // ─── 404 ──────────────────────────────────────────────────────────────────

    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ReservationNotFoundException ex) {
        return error("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProductNotFound(ProductNotFoundException ex) {
        return error("PRODUCT_NOT_FOUND", ex.getMessage());
    }

    // ─── 409 ──────────────────────────────────────────────────────────────────

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInsufficientStock(InsufficientStockException ex) {
        return error("INSUFFICIENT_STOCK", ex.getMessage());
    }

    @ExceptionHandler(LockAcquisitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleLockConflict(LockAcquisitionException ex) {
        return error("LOCK_CONFLICT", "System is busy, please retry: " + ex.getMessage());
    }

    // ─── 500 ──────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return error("INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ErrorResponse error(String code, String message) {
        return new ErrorResponse(code, message, Instant.now());
    }

    public record ErrorResponse(String code, String message, Instant timestamp) {}
}
