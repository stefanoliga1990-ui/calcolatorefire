package com.example.calcolatorefire.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.example.calcolatorefire.domain.AccumulationPoint;
import com.example.calcolatorefire.domain.DecumulationPoint;
import com.example.calcolatorefire.domain.ExistingInvestmentPoint;
import com.example.calcolatorefire.domain.ExistingInvestmentResult;
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
                        result.safeWithdrawalRateBaseTarget(),
                        result.safeWithdrawalRateTarget(),
                        result.selectedTarget(),
                        result.selectedTargetToday(),
                        result.firstMonthlyAdditionalIncome(),
                        result.firstMonthlyNetWithdrawal(),
                        result.finiteTargetProjectedFinalBalance()
                ),
                new Accumulation(
                        result.capitalGap(),
                        result.initialMonthlyContribution(),
                        result.totalNominalContributions(),
                        result.totalNominalAdditionalIncomeInvested(),
                        result.totalNominalExistingInvestmentContributions(),
                        result.projectedMainPortfolioFinalBalance(),
                        result.projectedInvestedIncomeFinalBalance(),
                        result.projectedAvailableExistingInvestmentsFinalBalance(),
                        result.projectedAccumulationFinalBalance(),
                        result.accumulationProjection().stream().map(AccumulationMonth::from).toList(),
                        result.existingInvestments().stream().map(ExistingInvestmentProjection::from).toList()
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
            Double safeWithdrawalRateBaseTarget,
            Double safeWithdrawalRateTarget,
            double selectedTarget,
            double selectedTargetToday,
            double firstMonthlyAdditionalIncome,
            double firstMonthlyNetWithdrawal,
            Double finiteTargetProjectedFinalBalance
    ) {
    }

    public record Accumulation(
            double capitalGap,
            double initialMonthlyContribution,
            double totalNominalContributions,
            double totalNominalAdditionalIncomeInvested,
            double totalNominalExistingInvestmentContributions,
            double mainPortfolioFinalBalance,
            double investedIncomeFinalBalance,
            double availableExistingInvestmentsFinalBalance,
            double projectedFinalBalance,
            List<AccumulationMonth> projection,
            List<ExistingInvestmentProjection> existingInvestments
    ) {
        public Accumulation {
            projection = List.copyOf(projection);
            existingInvestments = List.copyOf(existingInvestments);
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
            double additionalIncomeContribution,
            double closingBalance,
            double cumulativeContributions,
            double cumulativeAdditionalIncome,
            double availableExistingInvestmentsBalance,
            double totalAvailableBalance
    ) {
        static AccumulationMonth from(AccumulationPoint point) {
            return new AccumulationMonth(
                    point.month(),
                    point.age(),
                    point.openingBalance(),
                    point.investmentReturn(),
                    point.contribution(),
                    point.additionalIncomeContribution(),
                    point.closingBalance(),
                    point.cumulativeContributions(),
                    point.cumulativeAdditionalIncome(),
                    point.availableExistingInvestmentsBalance(),
                    point.totalAvailableBalance()
            );
        }
    }

    public record ExistingInvestmentProjection(
            int resourceIndex,
            String name,
            boolean availableAtFire,
            double totalNominalContributions,
            double finalBalance,
            List<ExistingInvestmentMonth> projection
    ) {
        public ExistingInvestmentProjection {
            projection = List.copyOf(projection);
        }

        static ExistingInvestmentProjection from(ExistingInvestmentResult result) {
            return new ExistingInvestmentProjection(
                    result.resourceIndex(),
                    result.name(),
                    result.availableAtFire(),
                    result.totalNominalContributions(),
                    result.finalBalance(),
                    result.projection().stream().map(ExistingInvestmentMonth::from).toList()
            );
        }
    }

    public record ExistingInvestmentMonth(
            int month,
            double age,
            double openingBalance,
            double investmentReturn,
            double contribution,
            double closingBalance,
            double cumulativeContributions
    ) {
        static ExistingInvestmentMonth from(ExistingInvestmentPoint point) {
            return new ExistingInvestmentMonth(
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
            double grossExpense,
            double additionalIncome,
            double scheduledWithdrawal,
            double capitalInflow,
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
                    point.grossExpense(),
                    point.additionalIncome(),
                    point.scheduledWithdrawal(),
                    point.capitalInflow(),
                    point.actualWithdrawal(),
                    point.shortfall(),
                    point.investmentReturn(),
                    point.closingBalance()
            );
        }
    }
}
