package com.example.calcolatorefire.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.example.calcolatorefire.domain.AccumulationPoint;
import com.example.calcolatorefire.domain.DecumulationPoint;
import com.example.calcolatorefire.domain.FireCalculationResult;

public record FireCalculationResponse(
        int accumulationMonths,
        int fireMonths,
        Rates rates,
        Target target,
        Accumulation accumulation,
        Decumulation decumulation
) {

    public static FireCalculationResponse from(FireCalculationResult result) {
        return new FireCalculationResponse(
                result.accumulationMonths(),
                result.fireMonths(),
                new Rates(
                        result.monthlyInflationRate(),
                        result.monthlyFireReturnRate(),
                        result.monthlyRealFireReturnRate(),
                        result.monthlyAccumulationReturnRate(),
                        result.monthlyContributionGrowthRate()
                ),
                new Target(
                        result.firstMonthlyWithdrawal(),
                        result.terminalCapitalAtFire(),
                        result.terminalCapitalNominalAtEnd(),
                        result.finiteTarget(),
                        result.safeWithdrawalRateTarget(),
                        result.selectedTarget(),
                        result.selectedTargetToday(),
                        result.finiteTargetProjectedFinalBalance()
                ),
                new Accumulation(
                        result.capitalGap(),
                        result.initialMonthlyContribution(),
                        result.totalNominalContributions(),
                        result.projectedAccumulationFinalBalance(),
                        result.accumulationProjection().stream().map(AccumulationMonth::from).toList()
                ),
                new Decumulation(
                        result.targetDecumulationFinalBalance(),
                        result.personalDecumulationStartBalance(),
                        result.personalDecumulationFinalBalance(),
                        result.totalShortfall(),
                        result.depletionMonth(),
                        result.decumulationProjection().stream().map(DecumulationMonth::from).toList()
                )
        );
    }

    public record Rates(
            double monthlyInflationRate,
            double monthlyFireReturnRate,
            double monthlyRealFireReturnRate,
            double monthlyAccumulationReturnRate,
            double monthlyContributionGrowthRate
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Target(
            double firstMonthlyWithdrawal,
            double terminalCapitalAtFire,
            double terminalCapitalNominalAtEnd,
            Double finiteTarget,
            Double safeWithdrawalRateTarget,
            double selectedTarget,
            double selectedTargetToday,
            Double finiteTargetProjectedFinalBalance
    ) {
    }

    public record Accumulation(
            double capitalGap,
            double initialMonthlyContribution,
            double totalNominalContributions,
            double projectedFinalBalance,
            List<AccumulationMonth> projection
    ) {
        public Accumulation {
            projection = List.copyOf(projection);
        }
    }

    public record Decumulation(
            double targetFinalBalance,
            double personalStartBalance,
            double personalFinalBalance,
            double totalShortfall,
            Integer depletionMonth,
            List<DecumulationMonth> projection
    ) {
        public Decumulation {
            projection = List.copyOf(projection);
        }
    }

    public record AccumulationMonth(
            int month,
            double age,
            double openingBalance,
            double investmentReturn,
            double contribution,
            double closingBalance,
            double cumulativeContributions
    ) {
        static AccumulationMonth from(AccumulationPoint point) {
            return new AccumulationMonth(
                    point.month(),
                    point.age(),
                    point.openingBalance(),
                    point.investmentReturn(),
                    point.contribution(),
                    point.closingBalance(),
                    point.cumulativeContributions()
            );
        }
    }

    public record DecumulationMonth(
            int month,
            double age,
            double openingBalance,
            double scheduledWithdrawal,
            double actualWithdrawal,
            double shortfall,
            double investmentReturn,
            double closingBalance
    ) {
        static DecumulationMonth from(DecumulationPoint point) {
            return new DecumulationMonth(
                    point.month(),
                    point.age(),
                    point.openingBalance(),
                    point.scheduledWithdrawal(),
                    point.actualWithdrawal(),
                    point.shortfall(),
                    point.investmentReturn(),
                    point.closingBalance()
            );
        }
    }
}
