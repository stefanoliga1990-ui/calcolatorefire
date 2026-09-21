package com.example.calcolatorefire.api;

import com.example.calcolatorefire.domain.AdditionalResource;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ExistingInvestmentRequest.class, name = "EXISTING_INVESTMENT"),
        @JsonSubTypes.Type(value = PeriodicIncomeRequest.class, name = "PERIODIC_INCOME"),
        @JsonSubTypes.Type(value = FutureLumpSumRequest.class, name = "FUTURE_LUMP_SUM")
})
public sealed interface AdditionalResourceRequest permits
        ExistingInvestmentRequest,
        PeriodicIncomeRequest,
        FutureLumpSumRequest {

    AdditionalResource toDomain();
}
