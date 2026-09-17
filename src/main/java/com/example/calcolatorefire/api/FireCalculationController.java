package com.example.calcolatorefire.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.calcolatorefire.application.FireCalculationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/fire/calculations")
public class FireCalculationController {

    private final FireCalculationService service;

    public FireCalculationController(FireCalculationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public FireCalculationResponse calculate(@Valid @RequestBody FireCalculationRequest request) {
        return FireCalculationResponse.from(service.calculate(request.toDomain()));
    }
}
