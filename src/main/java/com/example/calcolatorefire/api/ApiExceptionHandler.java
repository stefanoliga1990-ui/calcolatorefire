package com.example.calcolatorefire.api;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.calcolatorefire.domain.FireCalculationException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(FireCalculationException.class)
    ResponseEntity<ProblemDetail> handleCalculationError(
            FireCalculationException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Calcolo non valido",
                exception.getMessage(),
                request
        );
        problem.setProperty("code", exception.code().name());
        return ResponseEntity.unprocessableContent().body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidationError(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "Valore non valido" : error.getDefaultMessage()
                ))
                .sorted(Comparator.comparing(error -> error.get("field")))
                .toList();

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Input non validi",
                "Uno o più campi non sono validi.",
                request
        );
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Richiesta non leggibile",
                "Il corpo JSON è malformato o contiene un valore non riconosciuto.",
                request
        );
        problem.setProperty("code", "MALFORMED_REQUEST");
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
