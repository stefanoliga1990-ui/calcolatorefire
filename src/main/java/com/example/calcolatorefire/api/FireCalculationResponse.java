package com.example.calcolatorefire.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.example.calcolatorefire.domain.AccumulationPoint;
import com.example.calcolatorefire.domain.DecumulationPoint;
import com.example.calcolatorefire.domain.ExistingInvestmentPoint;
import com.example.calcolatorefire.domain.ExistingInvestmentResult;
import com.example.calcolatorefire.domain.FireCalculationResult;
import com.example.calcolatorefire.domain.FireCalculationInput;
import com.example.calcolatorefire.domain.FiscalFireCalculationResult;
import com.example.calcolatorefire.domain.FutureLumpSumResult;
import com.example.calcolatorefire.domain.FutureLumpSumPoint;
import com.example.calcolatorefire.domain.PeriodicIncomePoint;
import com.example.calcolatorefire.domain.PeriodicIncomeResult;

public record FireCalculationResponse(
        int accumulationMonths,
        int fireMonths,
        Rates rates,
        Target target,
        Accumulation accumulation,
        Decumulation decumulation,
        FiscalCalculationResponse fiscal
) {

    public static FireCalculationResponse from(
            FireCalculationResult result,
            FiscalFireCalculationResult fiscalResult,
            FireCalculationInput input
    ) {
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
                        result.projectedAvailableFutureLumpSumsFinalBalance(),
                        result.projectedAccumulationFinalBalance(),
                        result.accumulationProjection().stream().map(AccumulationMonth::from).toList(),
                        result.existingInvestments().stream().map(ExistingInvestmentProjection::from).toList(),
                        result.periodicIncomes().stream().map(PeriodicIncomeProjection::from).toList(),
                        result.futureLumpSums().stream().map(FutureLumpSumProjection::from).toList()
                ),
                new Decumulation(
                        result.targetDecumulationFinalBalance(),
                        result.personalDecumulationStartBalance(),
                        result.personalDecumulationFinalBalance(),
                        result.totalCapitalInflows(),
                        result.terminalCapitalInflow(),
                        result.totalShortfall(),
                        result.depletionMonth(),
                        result.decumulationProjection().stream().map(DecumulationMonth::from).toList()
                ),
                FiscalCalculationResponse.from(fiscalResult, input, result)
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
            double availableFutureLumpSumsFinalBalance,
            double projectedFinalBalance,
            List<AccumulationMonth> projection,
            List<ExistingInvestmentProjection> existingInvestments,
            List<PeriodicIncomeProjection> periodicIncomes,
            List<FutureLumpSumProjection> futureLumpSums
    ) {
        public Accumulation {
            projection = List.copyOf(projection);
            existingInvestments = List.copyOf(existingInvestments);
            periodicIncomes = List.copyOf(periodicIncomes);
            futureLumpSums = List.copyOf(futureLumpSums);
        }
    }

    public record Decumulation(
            double targetFinalBalance,
            double personalStartBalance,
            double personalFinalBalance,
            double totalCapitalInflows,
            double terminalCapitalInflow,
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
            double availableFutureLumpSumsBalance,
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
                    point.availableFutureLumpSumsBalance(),
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

    public record PeriodicIncomeProjection(
            int resourceIndex,
            String name,
            boolean investBeforeFire,
            boolean offsetDuringFire,
            List<PeriodicIncomeMonth> projection
    ) {
        public PeriodicIncomeProjection {
            projection = List.copyOf(projection);
        }

        static PeriodicIncomeProjection from(PeriodicIncomeResult result) {
            return new PeriodicIncomeProjection(
                    result.resourceIndex(),
                    result.name(),
                    result.investBeforeFire(),
                    result.offsetDuringFire(),
                    result.projection().stream().map(PeriodicIncomeMonth::from).toList()
            );
        }
    }

    public record PeriodicIncomeMonth(
            int month,
            double age,
            double monthlyAmount
    ) {
        static PeriodicIncomeMonth from(PeriodicIncomePoint point) {
            return new PeriodicIncomeMonth(point.month(), point.age(), point.monthlyAmount());
        }
    }

    public record FutureLumpSumProjection(
            int resourceIndex,
            String name,
            String amountBasis,
            int receiptMonth,
            double receiptAge,
            double nominalAmountAtReceipt,
            double balanceAtFire,
            Integer fireReceiptMonth,
            List<FutureLumpSumMonth> projection
    ) {
        public FutureLumpSumProjection {
            projection = List.copyOf(projection);
        }

        static FutureLumpSumProjection from(FutureLumpSumResult result) {
            return new FutureLumpSumProjection(
                    result.resourceIndex(),
                    result.name(),
                    result.amountBasis().name(),
                    result.receiptMonth(),
                    result.receiptAge(),
                    result.nominalAmountAtReceipt(),
                    result.balanceAtFire(),
                    result.fireReceiptMonth(),
                    result.projection().stream().map(FutureLumpSumMonth::from).toList()
            );
        }
    }

    public record FutureLumpSumMonth(
            int month,
            double age,
            double availableAmount
    ) {
        static FutureLumpSumMonth from(FutureLumpSumPoint point) {
            return new FutureLumpSumMonth(point.month(), point.age(), point.availableAmount());
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
            double terminalCapitalInflow,
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
                    point.terminalCapitalInflow(),
                    point.actualWithdrawal(),
                    point.shortfall(),
                    point.investmentReturn(),
                    point.closingBalance()
            );
        }
    }
}
