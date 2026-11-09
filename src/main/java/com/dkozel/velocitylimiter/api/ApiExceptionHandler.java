package com.dkozel.velocitylimiter.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Spring's "retry may succeed" category; a real H2 lock timeout arrives as its QueryTimeoutException subclass
    @ExceptionHandler(TransientDataAccessException.class)
    ResponseEntity<ProblemDetail> handleBusyCustomer(TransientDataAccessException ex) {
        log.warn("Customer lock timed out, answered 503: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Another load for this customer is in progress; retry shortly.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected failure, answered 500", ex);
        return ResponseEntity.internalServerError()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Class<?> type = ex.getBindingResult().getTarget().getClass();
        ProblemDetail problem = ex.getBody();
        problem.setProperty("errors", ex.getFieldErrors().stream()
                .map(error -> Map.of(
                        "pointer", "#/" + JsonFieldNames.of(type, error.getField()),
                        "detail", error.getDefaultMessage()))
                .toList());
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = createProblemDetail(ex, status, "Failed to read request", null, null, request);
        // Jackson's path holds the JSON names the client sent; its message is not shown (it can echo internals)
        if (ex.getCause() instanceof DatabindException unreadable && !unreadable.getPath().isEmpty()) {
            String pointer = "#/" + unreadable.getPath().stream()
                    .map(JacksonException.Reference::getPropertyName)
                    .collect(Collectors.joining("/"));
            String detail = unreadable instanceof UnrecognizedPropertyException
                    ? "unknown field"
                    : "has a value of the wrong type or format";
            problem.setProperty("errors", List.of(Map.of("pointer", pointer, "detail", detail)));
        }
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
