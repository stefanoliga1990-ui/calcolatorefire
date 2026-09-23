package com.example.calcolatorefire.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.example.calcolatorefire.domain.FireCalculationInput;
import com.example.calcolatorefire.domain.FireCalculationResult;
import com.example.calcolatorefire.domain.FiscalAccumulationProjection;
import com.example.calcolatorefire.domain.FiscalAccumulationProjectionPoint;
import com.example.calcolatorefire.domain.FiscalCalculator;
import com.example.calcolatorefire.domain.FiscalDecumulationProjection;
import com.example.calcolatorefire.domain.FiscalDecumulationProjectionPoint;
import com.example.calcolatorefire.domain.FiscalFireCalculationResult;

public record FiscalCalculationResponse(
        Settings settings,
        Target target,
        Accumulation accumulation,
        Decumulation decumulation,
        Totals totals
) {

    public static FiscalCalculationResponse from(
            FiscalFireCalculationResult result,
            FireCalculationInput input,
            FireCalculationResult legacyResult
    ) {
        FiscalDecumulationProjectionPoint firstMonth = result.targetDecumulation().points().get(1);
        double inflationFactor = Math.pow(
                1.0 + legacyResult.monthlyInflationRate(),
                result.accumulationMonths()
        );
        double selectedTargetToday = inflationFactor == 0.0
                ? result.selectedTarget()
                : result.selectedTarget() / inflationFactor;
        double targetLatentGain = Math.max(0.0, result.selectedTarget() - result.selectedTargetTaxBasis());
        double accumulationStamp = result.accumulation().totalStampDuty();
        double fireTax = result.personalDecumulation().totalCapitalGainsTax();
        double fireStamp = result.personalDecumulation().totalStampDuty();

        return new FiscalCalculationResponse(
                new Settings(
                        result.settings().capitalGainsTaxRate(),
                        result.settings().annualStampDutyRate(),
                        new FiscalCalculator().monthlyStampDutyRate(result.settings())
                ),
                new Target(
                        result.finiteTarget(),
                        result.safeWithdrawalRateBaseTarget(),
                        result.safeWithdrawalRateTarget(),
                        result.selectedTarget(),
                        selectedTargetToday,
                        result.selectedTargetTaxBasis(),
                        targetLatentGain,
                        firstMonth.requiredGrossSale(),
                        firstMonth.grossSale(),
                        firstMonth.capitalGainsTax(),
                        firstMonth.netProceeds()
                ),
                new Accumulation(
                        result.initialMonthlyContribution(),
                        result.accumulation().availableAtFireState().balance(),
                        result.accumulation().availableAtFireState().taxBasis(),
                        result.accumulation().availableAtFireState().latentGain(),
                        result.accumulation().totalStampDuty(),
                        result.accumulation().availableAtFireStampDuty(),
                        result.accumulation().projections().stream()
                                .map(projection -> Portfolio.from(projection, input.currentAge()))
                                .toList()
                ),
                new Decumulation(
                        Run.from(result.targetDecumulation(), input.fireAge()),
                        Run.from(result.personalDecumulation(), input.fireAge())
                ),
                new Totals(
                        0.0,
                        accumulationStamp,
                        fireTax,
                        fireStamp,
                        accumulationStamp + fireTax + fireStamp
                )
        );
    }

    public record Settings(
            double capitalGainsTaxRate,
            double annualStampDutyRate,
            double monthlyStampDutyRate
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Target(
            Double finiteTarget,
            Double safeWithdrawalRateBaseTarget,
            Double safeWithdrawalRateTarget,
            double selectedTarget,
            double selectedTargetToday,
            double taxBasis,
            double latentGain,
            double firstRequiredGrossSale,
            double firstGrossSale,
            double firstCapitalGainsTax,
            double firstNetProceeds
    ) {
    }

    public record Accumulation(
            double initialMonthlyContribution,
            double availableBalanceAtFire,
            double availableTaxBasisAtFire,
            double latentGainAtFire,
            double totalStampDuty,
            double availablePortfolioStampDuty,
            List<Portfolio> portfolios
    ) {
        public Accumulation {
            portfolios = List.copyOf(portfolios);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Portfolio(
            String sourceType,
            Integer resourceIndex,
            String name,
            boolean availableAtFire,
            boolean stampDutyApplicable,
            double initialBalance,
            double initialTaxBasis,
            double finalBalance,
            double finalTaxBasis,
            double latentGain,
            double totalContributions,
            double totalNetInflows,
            double totalInvestmentReturns,
            double totalStampDuty,
            List<AccumulationMonth> projection
    ) {
        public Portfolio {
            projection = List.copyOf(projection);
        }

        static Portfolio from(FiscalAccumulationProjection projection, int currentAge) {
            return new Portfolio(
                    projection.sourceType(),
                    projection.resourceIndex(),
                    projection.name(),
                    projection.availableAtFire(),
                    projection.stampDutyApplicable(),
                    projection.initialState().balance(),
                    projection.initialState().taxBasis(),
                    projection.finalState().balance(),
                    projection.finalState().taxBasis(),
                    projection.finalState().latentGain(),
                    projection.totalContributions(),
                    projection.totalNetInflows(),
                    projection.totalInvestmentReturns(),
                    projection.totalStampDuty(),
                    projection.points().stream()
                            .map(point -> AccumulationMonth.from(point, currentAge))
                            .toList()
            );
        }
    }

    public record AccumulationMonth(
            int month,
            double age,
            double openingBalance,
            double openingTaxBasis,
            double investmentReturn,
            double contribution,
            double netInflows,
            double grossBalance,
            double stampDuty,
            double closingBalance,
            double closingTaxBasis,
            double latentGain
    ) {
        static AccumulationMonth from(FiscalAccumulationProjectionPoint point, int currentAge) {
            return new AccumulationMonth(
                    point.month(),
                    currentAge + point.month() / 12.0,
                    point.openingState().balance(),
                    point.openingState().taxBasis(),
                    point.investmentReturn(),
                    point.contribution(),
                    point.netInflows(),
                    point.grossBalance(),
                    point.stampDuty(),
                    point.closingState().balance(),
                    point.closingState().taxBasis(),
                    point.closingState().latentGain()
            );
        }
    }

    public record Decumulation(
            Run target,
            Run personal
    ) {
    }

    public record Run(
            double initialBalance,
            double initialTaxBasis,
            double finalBalance,
            double finalTaxBasis,
            double totalGrossSales,
            double totalCapitalGainsTax,
            double totalNetProceeds,
            double totalStampDuty,
            double totalShortfall,
            Integer depletionMonth,
            List<DecumulationMonth> projection
    ) {
        public Run {
            projection = List.copyOf(projection);
        }

        static Run from(FiscalDecumulationProjection projection, int fireAge) {
            return new Run(
                    projection.initialState().balance(),
                    projection.initialState().taxBasis(),
                    projection.finalState().balance(),
                    projection.finalState().taxBasis(),
                    projection.totalGrossSales(),
                    projection.totalCapitalGainsTax(),
                    projection.totalNetProceeds(),
                    projection.totalStampDuty(),
                    projection.totalShortfall(),
                    projection.depletionMonth(),
                    projection.points().stream()
                            .map(point -> DecumulationMonth.from(point, fireAge))
                            .toList()
            );
        }
    }

    public record DecumulationMonth(
            int month,
            double age,
            double openingBalance,
            double openingTaxBasis,
            double grossExpense,
            double additionalIncome,
            double requestedNetAmount,
            double netCapitalInflow,
            double taxableGainRatio,
            double requiredGrossSale,
            double grossSale,
            double capitalGainsTax,
            double netProceeds,
            double shortfall,
            double investmentReturn,
            double stampDuty,
            double terminalCapitalInflow,
            double closingBalance,
            double closingTaxBasis,
            double latentGain
    ) {
        static DecumulationMonth from(FiscalDecumulationProjectionPoint point, int fireAge) {
            return new DecumulationMonth(
                    point.month(),
                    fireAge + point.month() / 12.0,
                    point.openingState().balance(),
                    point.openingState().taxBasis(),
                    point.grossExpense(),
                    point.additionalIncome(),
                    point.requestedNetAmount(),
                    point.netCapitalInflow(),
                    point.taxableGainRatio(),
                    point.requiredGrossSale(),
                    point.grossSale(),
                    point.capitalGainsTax(),
                    point.netProceeds(),
                    point.shortfall(),
                    point.investmentReturn(),
                    point.stampDuty(),
                    point.terminalCapitalInflow(),
                    point.closingState().balance(),
                    point.closingState().taxBasis(),
                    point.closingState().latentGain()
            );
        }
    }

    public record Totals(
            double accumulationCapitalGainsTax,
            double accumulationStampDuty,
            double fireCapitalGainsTax,
            double fireStampDuty,
            double totalEstimatedTaxes
    ) {
    }
}
