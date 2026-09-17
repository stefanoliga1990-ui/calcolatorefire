package com.example.calcolatorefire.application;

import org.springframework.stereotype.Service;

import com.example.calcolatorefire.domain.FireCalculationInput;
import com.example.calcolatorefire.domain.FireCalculationResult;
import com.example.calcolatorefire.domain.FireCalculator;

@Service
public class FireCalculationService {

    private final FireCalculator calculator = new FireCalculator();

    public FireCalculationResult calculate(FireCalculationInput input) {
        return calculator.calculate(input);
    }
}
