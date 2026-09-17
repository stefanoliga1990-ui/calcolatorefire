package com.example.calcolatorefire.domain;

public final class FireCalculationException extends IllegalArgumentException {

    private final CalculationErrorCode code;

    public FireCalculationException(CalculationErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public CalculationErrorCode code() {
        return code;
    }
}
