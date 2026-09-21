package com.example.calcolatorefire.domain;

public sealed interface AdditionalResource permits ExistingInvestment, PeriodicIncome, FutureLumpSum {

    String name();
}
