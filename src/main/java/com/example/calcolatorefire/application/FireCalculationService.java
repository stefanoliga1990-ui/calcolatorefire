package com.example.calcolatorefire.application;

import org.springframework.stereotype.Service;

import com.example.calcolatorefire.domain.FireCalculationInput;
import com.example.calcolatorefire.domain.FireCalculationResult;
import com.example.calcolatorefire.domain.FireCalculator;
import com.example.calcolatorefire.domain.FiscalFireCalculationInput;
import com.example.calcolatorefire.domain.FiscalFireCalculationResult;
import com.example.calcolatorefire.domain.FiscalFireCalculator;

@Service
public class FireCalculationService {

    private final FireCalculator calculator = new FireCalculator();
    private final FiscalFireCalculator fiscalCalculator = new FiscalFireCalculator();

    public FireCalculationResult calculate(FireCalculationInput input) {
        return calculator.calculate(input);
    }

    public FiscalFireCalculationResult calculateFiscal(FiscalFireCalculationInput input) {
        return fiscalCalculator.calculate(input);
    }
}
