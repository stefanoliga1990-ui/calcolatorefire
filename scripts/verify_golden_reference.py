#!/usr/bin/env python3
"""Independent reference verifier for the FIRE golden scenarios.

This script implements the formulas in docs/specifica-matematica.md using only
Python's standard library.  It does not invoke the Java application and does
not import production code.  Golden values remain static acceptance data; the
script independently recalculates them and reports any difference above one
cent.
"""

from __future__ import annotations

import csv
import json
import math
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BASE_GOLDEN = ROOT / "src/test/resources/golden-scenarios.csv"
RESOURCE_GOLDEN = ROOT / "src/test/resources/golden-additional-resources.json"
TAX_GOLDEN = ROOT / "src/test/resources/golden-tax-scenarios.json"
TAX_ACCUMULATION_GOLDEN = ROOT / "src/test/resources/golden-tax-accumulation-scenarios.json"
MONEY_TOLERANCE = 0.01
RATE_TOLERANCE = 1e-12


def monthly_rate(annual_rate: float) -> float:
    return math.pow(1.0 + annual_rate, 1.0 / 12.0) - 1.0


def age_month(age: int, current_age: int) -> int:
    return 12 * (age - current_age)


def periodic_income_at(request: dict[str, Any], income: dict[str, Any], absolute_month: int) -> float:
    start = age_month(income["startAge"], request["currentAge"])
    end = (age_month(income["endAge"], request["currentAge"])
           if income.get("endAge") is not None else math.inf)
    if absolute_month < start or absolute_month >= end:
        return 0.0
    growth = monthly_rate(income["annualGrowthRate"])
    return income["monthlyAmountToday"] * math.pow(1.0 + growth, absolute_month)


def build_fire_cash_flows(
    request: dict[str, Any], accumulation_months: int, fire_months: int,
    first_withdrawal: float, monthly_inflation: float,
) -> dict[str, Any]:
    gross = [0.0] * (fire_months + 1)
    income = [0.0] * (fire_months + 1)
    net = [0.0] * (fire_months + 1)
    inflows = [0.0] * (fire_months + 1)
    resources = request.get("additionalResources") or []

    for month in range(1, fire_months + 1):
        absolute_month = accumulation_months + month - 1
        gross[month] = first_withdrawal * math.pow(1.0 + monthly_inflation, month - 1)
        income[month] = sum(
            periodic_income_at(request, resource, absolute_month)
            for resource in resources
            if resource["type"] == "PERIODIC_INCOME" and resource["offsetDuringFire"]
        )
        net[month] = max(0.0, gross[month] - income[month])

    fire_end = accumulation_months + fire_months
    terminal_inflow = 0.0
    for resource in resources:
        if resource["type"] != "FUTURE_LUMP_SUM":
            continue
        receipt = age_month(resource["receiptAge"], request["currentAge"])
        if receipt <= accumulation_months:
            continue
        nominal = resource["amount"]
        if resource["amountBasis"] == "TODAY":
            nominal *= math.pow(1.0 + monthly_inflation, receipt)
        if receipt < fire_end:
            inflows[receipt - accumulation_months + 1] += nominal
        elif receipt == fire_end:
            terminal_inflow += nominal

    return {
        "gross": gross,
        "income": income,
        "net": net,
        "inflows": inflows,
        "terminalInflow": terminal_inflow,
        "totalInflows": terminal_inflow + sum(inflows),
    }


def has_variable_fire_resources(cash_flows: dict[str, Any]) -> bool:
    return (any(value > 0.0 for value in cash_flows["income"][1:])
            or any(value > 0.0 for value in cash_flows["inflows"][1:])
            or cash_flows["terminalInflow"] > 0.0)


def finite_target_closed(
    first_withdrawal: float, terminal_at_fire: float,
    monthly_real_return: float, fire_months: int,
) -> float:
    if abs(monthly_real_return) < RATE_TOLERANCE:
        return first_withdrawal * fire_months + terminal_at_fire
    factor = ((1.0 - math.pow(1.0 + monthly_real_return, -fire_months))
              / monthly_real_return * (1.0 + monthly_real_return))
    return (first_withdrawal * factor
            + terminal_at_fire / math.pow(1.0 + monthly_real_return, fire_months))


def finite_target_with_resources(
    cash_flows: dict[str, Any], terminal_at_end: float,
    monthly_fire_return: float, fire_months: int,
) -> float:
    required = max(0.0, terminal_at_end - cash_flows["terminalInflow"])
    for month in range(fire_months, 0, -1):
        required = max(
            0.0,
            cash_flows["net"][month]
            + required / (1.0 + monthly_fire_return)
            - cash_flows["inflows"][month],
        )
    return required


def stable_regime_start(request: dict[str, Any], accumulation_months: int, fire_months: int) -> int:
    stable_month = 1
    fire_end = accumulation_months + fire_months
    for resource in request.get("additionalResources") or []:
        if resource["type"] == "PERIODIC_INCOME" and resource["offsetDuringFire"]:
            start = age_month(resource["startAge"], request["currentAge"])
            if accumulation_months <= start < fire_end:
                stable_month = max(stable_month, start - accumulation_months + 1)
            if resource.get("endAge") is not None:
                end = age_month(resource["endAge"], request["currentAge"])
                if accumulation_months <= end < fire_end:
                    stable_month = max(stable_month, end - accumulation_months + 1)
        elif resource["type"] == "FUTURE_LUMP_SUM":
            receipt = age_month(resource["receiptAge"], request["currentAge"])
            if accumulation_months < receipt < fire_end:
                stable_month = max(stable_month, receipt - accumulation_months + 1)
    return stable_month


def swr_target(
    request: dict[str, Any], cash_flows: dict[str, Any], base_target: float,
    monthly_fire_return: float, accumulation_months: int, fire_months: int,
) -> float:
    if not (any(value > 0.0 for value in cash_flows["income"][1:])
            or any(value > 0.0 for value in cash_flows["inflows"][1:])):
        return base_target
    stable = stable_regime_start(request, accumulation_months, fire_months)
    bridge = max(
        0.0,
        cash_flows["net"][stable] * 12.0 / request["annualSafeWithdrawalRate"]
        - cash_flows["inflows"][stable],
    )
    for month in range(stable - 1, 0, -1):
        bridge = max(
            0.0,
            cash_flows["net"][month]
            + bridge / (1.0 + monthly_fire_return)
            - cash_flows["inflows"][month],
        )
    return min(base_target, bridge)


def project_resources(
    request: dict[str, Any], accumulation_months: int,
    monthly_accumulation_return: float, monthly_inflation: float,
) -> dict[str, Any]:
    income_contributions = [0.0] * (accumulation_months + 1)
    income_balances = [0.0] * (accumulation_months + 1)
    existing_balances = [0.0] * (accumulation_months + 1)
    lump_balances = [0.0] * (accumulation_months + 1)
    existing_results: list[dict[str, Any]] = []
    lump_results: list[dict[str, Any]] = []
    total_existing_contributions = 0.0
    resources = request.get("additionalResources") or []

    for index, resource in enumerate(resources):
        if resource["type"] != "EXISTING_INVESTMENT":
            continue
        balance = resource["currentCapital"]
        total_contributions = 0.0
        resource_return = monthly_rate(resource["annualReturnRate"])
        contribution_growth = monthly_rate(resource["annualContributionGrowthRate"])
        start = (age_month(resource["contributionStartAge"], request["currentAge"])
                 if resource.get("contributionStartAge") is not None else None)
        end = (age_month(resource["contributionEndAge"], request["currentAge"])
               if resource.get("contributionEndAge") is not None else None)
        if resource["availableAtFire"]:
            existing_balances[0] += balance
        for month in range(1, accumulation_months + 1):
            absolute_month = month - 1
            contribution = 0.0
            if start is not None and start <= absolute_month < end:
                contribution = (resource["initialMonthlyContribution"]
                                * math.pow(1.0 + contribution_growth, absolute_month - start))
            balance = balance * (1.0 + resource_return) + contribution
            total_contributions += contribution
            if resource["availableAtFire"]:
                existing_balances[month] += balance
        total_existing_contributions += total_contributions
        existing_results.append({
            "resourceIndex": index,
            "availableAtFire": resource["availableAtFire"],
            "totalNominalContributions": total_contributions,
            "finalBalance": balance,
        })

    for index, resource in enumerate(resources):
        if resource["type"] != "FUTURE_LUMP_SUM":
            continue
        receipt = age_month(resource["receiptAge"], request["currentAge"])
        nominal = resource["amount"]
        if resource["amountBasis"] == "TODAY":
            nominal *= math.pow(1.0 + monthly_inflation, receipt)
        balance_at_fire = 0.0
        fire_receipt_month = None
        if receipt <= accumulation_months:
            resource_return = (monthly_rate(resource["annualReturnRateAfterReceipt"])
                               if resource["investAfterReceipt"] else 0.0)
            for month in range(receipt, accumulation_months + 1):
                lump_balances[month] += nominal * math.pow(1.0 + resource_return, month - receipt)
            balance_at_fire = nominal * math.pow(
                1.0 + resource_return, accumulation_months - receipt)
        else:
            fire_receipt_month = receipt - accumulation_months + 1
        lump_results.append({
            "resourceIndex": index,
            "nominalAmountAtReceipt": nominal,
            "balanceAtFire": balance_at_fire,
            "fireReceiptMonth": fire_receipt_month,
        })

    income_balance = 0.0
    total_income = 0.0
    for month in range(1, accumulation_months + 1):
        absolute_month = month - 1
        contribution = sum(
            periodic_income_at(request, resource, absolute_month)
            for resource in resources
            if resource["type"] == "PERIODIC_INCOME" and resource["investBeforeFire"]
        )
        income_balance = income_balance * (1.0 + monthly_accumulation_return) + contribution
        total_income += contribution
        income_contributions[month] = contribution
        income_balances[month] = income_balance

    return {
        "incomeContributions": income_contributions,
        "incomeBalances": income_balances,
        "existingBalances": existing_balances,
        "lumpBalances": lump_balances,
        "totalIncome": total_income,
        "totalExistingContributions": total_existing_contributions,
        "investedIncomeFinalBalance": income_balance,
        "availableExistingFinalBalance": existing_balances[-1],
        "availableLumpFinalBalance": lump_balances[-1],
        "existingInvestments": existing_results,
        "futureLumpSums": lump_results,
    }


def contribution_factor(months: int, monthly_return: float, monthly_growth: float) -> float:
    if months == 0:
        return 0.0
    if abs(monthly_return - monthly_growth) < RATE_TOLERANCE:
        return months * math.pow(1.0 + monthly_return, months - 1)
    return ((math.pow(1.0 + monthly_return, months) - math.pow(1.0 + monthly_growth, months))
            / (monthly_return - monthly_growth))


def project_decumulation(
    starting_balance: float, cash_flows: dict[str, Any],
    monthly_return: float, fire_months: int,
) -> dict[str, Any]:
    balance = starting_balance
    total_shortfall = 0.0
    depletion_month = None
    points = [{"month": 0, "closingBalance": balance}]
    for month in range(1, fire_months + 1):
        available = balance + cash_flows["inflows"][month]
        scheduled = cash_flows["net"][month]
        actual = min(max(available, 0.0), scheduled)
        shortfall = scheduled - actual
        balance = max(0.0, (available - actual) * (1.0 + monthly_return))
        terminal = cash_flows["terminalInflow"] if month == fire_months else 0.0
        balance += terminal
        total_shortfall += shortfall
        if depletion_month is None and shortfall > MONEY_TOLERANCE:
            depletion_month = month
        points.append({
            "month": month,
            "grossExpense": cash_flows["gross"][month],
            "additionalIncome": cash_flows["income"][month],
            "scheduledWithdrawal": scheduled,
            "capitalInflow": cash_flows["inflows"][month],
            "terminalCapitalInflow": terminal,
            "actualWithdrawal": actual,
            "shortfall": shortfall,
            "closingBalance": balance,
        })
    return {
        "finalBalance": balance,
        "totalShortfall": total_shortfall,
        "depletionMonth": depletion_month,
        "points": points,
    }


def calculate(request: dict[str, Any]) -> dict[str, Any]:
    accumulation_months = 12 * (request["fireAge"] - request["currentAge"])
    fire_months = 12 * request["fireDurationYears"]
    monthly_inflation = monthly_rate(request["annualInflationRate"])
    monthly_fire_return = monthly_rate(request["annualFireReturnRate"])
    monthly_accumulation_return = monthly_rate(request["annualAccumulationReturnRate"])
    monthly_contribution_growth = monthly_rate(request["annualContributionGrowthRate"])
    monthly_real_return = ((1.0 + monthly_fire_return) / (1.0 + monthly_inflation) - 1.0)
    inflation_to_fire = math.pow(1.0 + monthly_inflation, accumulation_months)
    first_withdrawal = request["monthlyExpenseToday"] * inflation_to_fire
    terminal_at_fire = request["terminalCapitalToday"] * inflation_to_fire
    terminal_at_end = (request["terminalCapitalToday"]
                       * math.pow(1.0 + monthly_inflation, accumulation_months + fire_months))
    cash_flows = build_fire_cash_flows(
        request, accumulation_months, fire_months, first_withdrawal, monthly_inflation)

    finite = None
    swr_base = None
    swr = None
    if request["method"] == "FINITE":
        finite = (finite_target_with_resources(
            cash_flows, terminal_at_end, monthly_fire_return, fire_months)
            if has_variable_fire_resources(cash_flows)
            else finite_target_closed(first_withdrawal, terminal_at_fire, monthly_real_return, fire_months))
        selected = finite
    else:
        swr_base = first_withdrawal * 12.0 / request["annualSafeWithdrawalRate"]
        swr = swr_target(
            request, cash_flows, swr_base, monthly_fire_return, accumulation_months, fire_months)
        selected = swr

    resources = project_resources(
        request, accumulation_months, monthly_accumulation_return, monthly_inflation)
    projected_current = (request["currentCapital"]
                         * math.pow(1.0 + monthly_accumulation_return, accumulation_months))
    available_before_new_pac = (
        projected_current
        + resources["investedIncomeFinalBalance"]
        + resources["availableExistingFinalBalance"]
        + resources["availableLumpFinalBalance"]
    )
    gap = max(0.0, selected - available_before_new_pac)
    factor = contribution_factor(
        accumulation_months, monthly_accumulation_return, monthly_contribution_growth)
    initial_contribution = 0.0 if gap == 0.0 or accumulation_months == 0 else gap / factor

    primary_balance = request["currentCapital"]
    total_contributions = 0.0
    accumulation_points = [{
        "month": 0,
        "contribution": 0.0,
        "additionalIncomeContribution": 0.0,
        "closingBalance": primary_balance,
        "availableExistingInvestmentsBalance": resources["existingBalances"][0],
        "availableFutureLumpSumsBalance": resources["lumpBalances"][0],
        "totalAvailableBalance": (primary_balance + resources["existingBalances"][0]
                                  + resources["lumpBalances"][0]),
    }]
    for month in range(1, accumulation_months + 1):
        contribution = initial_contribution * math.pow(1.0 + monthly_contribution_growth, month - 1)
        total_contributions += contribution
        primary_balance = primary_balance * (1.0 + monthly_accumulation_return) + contribution
        closing = primary_balance + resources["incomeBalances"][month]
        total_available = (closing + resources["existingBalances"][month]
                           + resources["lumpBalances"][month])
        accumulation_points.append({
            "month": month,
            "contribution": contribution,
            "additionalIncomeContribution": resources["incomeContributions"][month],
            "closingBalance": closing,
            "availableExistingInvestmentsBalance": resources["existingBalances"][month],
            "availableFutureLumpSumsBalance": resources["lumpBalances"][month],
            "totalAvailableBalance": total_available,
        })
    projected_final = accumulation_points[-1]["totalAvailableBalance"]

    target_run = project_decumulation(selected, cash_flows, monthly_fire_return, fire_months)
    personal_start = max(selected, projected_final)
    personal_run = project_decumulation(personal_start, cash_flows, monthly_fire_return, fire_months)
    finite_projected_final = None
    if finite is not None:
        raw = finite
        for month in range(1, fire_months + 1):
            raw = ((raw + cash_flows["inflows"][month] - cash_flows["net"][month])
                   * (1.0 + monthly_fire_return))
        finite_projected_final = raw + cash_flows["terminalInflow"]

    return {
        "accumulationMonths": accumulation_months,
        "fireMonths": fire_months,
        "firstMonthlyWithdrawal": first_withdrawal,
        "terminalCapitalNominalAtEnd": terminal_at_end,
        "finiteTarget": finite,
        "safeWithdrawalRateBaseTarget": swr_base,
        "safeWithdrawalRateTarget": swr,
        "selectedTarget": selected,
        "initialMonthlyContribution": initial_contribution,
        "totalNominalContributions": total_contributions,
        "projectedFinalBalance": projected_final,
        "finiteTargetProjectedFinalBalance": finite_projected_final,
        "targetDecumulationFinalBalance": target_run["finalBalance"],
        "personalStartBalance": personal_start,
        "personalFinalBalance": personal_run["finalBalance"],
        "investedIncomeFinalBalance": resources["investedIncomeFinalBalance"],
        "availableExistingInvestmentsFinalBalance": resources["availableExistingFinalBalance"],
        "availableFutureLumpSumsFinalBalance": resources["availableLumpFinalBalance"],
        "totalNominalAdditionalIncomeInvested": resources["totalIncome"],
        "totalNominalExistingInvestmentContributions": resources["totalExistingContributions"],
        "firstMonthlyAdditionalIncome": cash_flows["income"][1],
        "firstMonthlyNetWithdrawal": cash_flows["net"][1],
        "totalCapitalInflows": cash_flows["totalInflows"],
        "terminalCapitalInflow": cash_flows["terminalInflow"],
        "totalShortfall": personal_run["totalShortfall"],
        "depletionMonth": personal_run["depletionMonth"],
        "accumulationPoints": accumulation_points,
        "decumulationPoints": personal_run["points"],
        "existingInvestments": resources["existingInvestments"],
        "futureLumpSums": resources["futureLumpSums"],
    }


def assert_money(errors: list[str], scenario: str, field: str, expected: Any, actual: Any) -> None:
    if expected is None or expected == "":
        return
    if actual is None or not math.isclose(float(expected), float(actual), rel_tol=0.0, abs_tol=MONEY_TOLERANCE):
        errors.append(f"{scenario}: {field}: expected {expected}, reference {actual}")


def assert_exact(errors: list[str], scenario: str, field: str, expected: Any, actual: Any) -> None:
    if expected != actual:
        errors.append(f"{scenario}: {field}: expected {expected}, reference {actual}")


def request_from_csv(row: dict[str, str]) -> dict[str, Any]:
    return {
        "method": row["method"],
        "currentAge": int(row["current_age"]),
        "fireAge": int(row["fire_age"]),
        "fireDurationYears": int(row["fire_years"]),
        "monthlyExpenseToday": float(row["expense_monthly_today"]),
        "annualInflationRate": float(row["inflation_annual"]),
        "annualFireReturnRate": float(row["fire_return_annual"]),
        "annualSafeWithdrawalRate": float(row["swr_annual"]) if row["swr_annual"] else None,
        "terminalCapitalToday": float(row["terminal_capital_today"]),
        "currentCapital": float(row["current_capital"]),
        "annualAccumulationReturnRate": float(row["accumulation_return_annual"]),
        "annualContributionGrowthRate": float(row["contribution_growth_annual"]),
        "additionalResources": [],
    }


def verify_base(errors: list[str]) -> int:
    field_map = {
        "expected_accumulation_months": "accumulationMonths",
        "expected_fire_months": "fireMonths",
        "expected_first_withdrawal": "firstMonthlyWithdrawal",
        "expected_finite_target": "finiteTarget",
        "expected_swr_target": "safeWithdrawalRateTarget",
        "expected_selected_target": "selectedTarget",
        "expected_initial_monthly_contribution": "initialMonthlyContribution",
        "expected_accumulation_final": "projectedFinalBalance",
        "expected_terminal_nominal": "terminalCapitalNominalAtEnd",
        "expected_finite_base_final": "finiteTargetProjectedFinalBalance",
        "expected_target_decumulation_final": "targetDecumulationFinalBalance",
        "expected_personal_decumulation_start": "personalStartBalance",
        "expected_personal_decumulation_final": "personalFinalBalance",
    }
    count = 0
    with BASE_GOLDEN.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            count += 1
            result = calculate(request_from_csv(row))
            for expected_field, actual_field in field_map.items():
                assert_money(errors, row["scenario_id"], expected_field, row[expected_field], result[actual_field])
    return count


def find_by_index(values: list[dict[str, Any]], index: int) -> dict[str, Any] | None:
    return next((value for value in values if value["resourceIndex"] == index), None)


def verify_resources(errors: list[str]) -> int:
    aggregate_fields = (
        "accumulationMonths", "fireMonths", "finiteTarget", "safeWithdrawalRateBaseTarget",
        "safeWithdrawalRateTarget", "selectedTarget", "initialMonthlyContribution",
        "projectedFinalBalance", "investedIncomeFinalBalance",
        "availableExistingInvestmentsFinalBalance", "availableFutureLumpSumsFinalBalance",
        "totalNominalAdditionalIncomeInvested", "totalNominalExistingInvestmentContributions",
        "personalFinalBalance", "firstMonthlyAdditionalIncome", "firstMonthlyNetWithdrawal",
        "totalCapitalInflows", "terminalCapitalInflow", "totalShortfall",
    )
    scenarios = json.loads(RESOURCE_GOLDEN.read_text(encoding="utf-8"))
    for scenario in scenarios:
        scenario_id = scenario["scenarioId"]
        result = calculate(scenario["request"])
        expected = scenario["expected"]
        for field in aggregate_fields:
            assert_money(errors, scenario_id, field, expected.get(field), result[field])
        assert_exact(errors, scenario_id, "depletionMonth", expected.get("depletionMonth"), result["depletionMonth"])

        for checkpoint in expected.get("accumulationCheckpoints", []):
            actual = result["accumulationPoints"][checkpoint["month"]]
            for field, value in checkpoint.items():
                if field != "month":
                    assert_money(errors, scenario_id, f"accumulation[{checkpoint['month']}].{field}", value, actual[field])
        for checkpoint in expected.get("decumulationCheckpoints", []):
            actual = result["decumulationPoints"][checkpoint["month"]]
            for field, value in checkpoint.items():
                if field != "month":
                    assert_money(errors, scenario_id, f"decumulation[{checkpoint['month']}].{field}", value, actual[field])
        for investment in expected.get("existingInvestments", []):
            actual = find_by_index(result["existingInvestments"], investment["resourceIndex"])
            if actual is None:
                errors.append(f"{scenario_id}: missing existing investment {investment['resourceIndex']}")
                continue
            for field, value in investment.items():
                if field == "availableAtFire":
                    assert_exact(errors, scenario_id, field, value, actual[field])
                elif field != "resourceIndex":
                    assert_money(errors, scenario_id, field, value, actual[field])
        for lump_sum in expected.get("futureLumpSums", []):
            actual = find_by_index(result["futureLumpSums"], lump_sum["resourceIndex"])
            if actual is None:
                errors.append(f"{scenario_id}: missing future lump sum {lump_sum['resourceIndex']}")
                continue
            for field, value in lump_sum.items():
                if field == "fireReceiptMonth":
                    assert_exact(errors, scenario_id, field, value, actual[field])
                elif field != "resourceIndex":
                    assert_money(errors, scenario_id, field, value, actual[field])
    return len(scenarios)


def calculate_tax_primitive(values: dict[str, Any]) -> dict[str, float]:
    balance = float(values["balance"])
    tax_basis = float(values["taxBasis"])
    net_need = float(values["netNeed"])
    tax_rate = float(values["capitalGainsTaxRate"])
    annual_stamp_rate = float(values["annualStampDutyRate"])

    taxable_gain_ratio = max(0.0, balance - tax_basis) / balance if balance > 0.0 else 0.0
    net_sale_factor = 1.0 - tax_rate * taxable_gain_ratio
    required_sale = net_need / net_sale_factor if net_sale_factor > 0.0 else math.inf
    gross_sale = min(balance, required_sale)
    capital_gains_tax = gross_sale * taxable_gain_ratio * tax_rate
    net_proceeds = gross_sale - capital_gains_tax
    remaining_tax_basis = (
        tax_basis * (1.0 - gross_sale / balance) if balance > 0.0 and gross_sale < balance else 0.0
    )
    balance_after_sale = balance - gross_sale
    monthly_stamp_rate = 1.0 - math.pow(1.0 - annual_stamp_rate, 1.0 / 12.0)
    stamp_duty = balance_after_sale * monthly_stamp_rate
    closing_balance = max(0.0, balance_after_sale - stamp_duty)
    if closing_balance == 0.0:
        remaining_tax_basis = 0.0

    return {
        "taxableGainRatio": taxable_gain_ratio,
        "grossSale": gross_sale,
        "capitalGainsTax": capital_gains_tax,
        "netProceeds": net_proceeds,
        "remainingTaxBasis": remaining_tax_basis,
        "stampDuty": stamp_duty,
        "closingBalance": closing_balance,
    }


def verify_tax_primitives(errors: list[str]) -> int:
    scenarios = json.loads(TAX_GOLDEN.read_text(encoding="utf-8"))
    for scenario in scenarios:
        scenario_id = scenario["scenarioId"]
        result = calculate_tax_primitive(scenario["input"])
        for field, expected in scenario["expected"].items():
            tolerance = RATE_TOLERANCE if field == "taxableGainRatio" else MONEY_TOLERANCE
            actual = result[field]
            if not math.isclose(float(expected), float(actual), rel_tol=0.0, abs_tol=tolerance):
                errors.append(f"{scenario_id}: {field}: expected {expected}, reference {actual}")
    return len(scenarios)


def calculate_tax_accumulation(values: dict[str, Any]) -> dict[str, float]:
    balance = float(values["balance"])
    tax_basis = float(values["taxBasis"])
    monthly_return = float(values["monthlyReturnRate"])
    annual_stamp_rate = float(values["annualStampDutyRate"])
    contributions = [float(value) for value in values["monthlyContributions"]]
    net_inflows = [float(value) for value in values["monthlyNetInflows"]]
    monthly_stamp_rate = 1.0 - math.pow(1.0 - annual_stamp_rate, 1.0 / 12.0)
    total_returns = 0.0
    total_stamp = 0.0
    first_closing_balance = balance

    for month, (contribution, net_inflow) in enumerate(zip(contributions, net_inflows), start=1):
        investment_return = balance * monthly_return
        gross_balance = balance + investment_return + contribution + net_inflow
        stamp_duty = gross_balance * monthly_stamp_rate
        balance = max(0.0, gross_balance - stamp_duty)
        tax_basis += contribution + net_inflow
        total_returns += investment_return
        total_stamp += stamp_duty
        if month == 1:
            first_closing_balance = balance

    return {
        "firstMonthClosingBalance": first_closing_balance,
        "finalBalance": balance,
        "finalTaxBasis": tax_basis,
        "latentGain": max(0.0, balance - tax_basis),
        "totalContributions": sum(contributions),
        "totalNetInflows": sum(net_inflows),
        "totalInvestmentReturns": total_returns,
        "totalStampDuty": total_stamp,
    }


def verify_tax_accumulation(errors: list[str]) -> int:
    scenarios = json.loads(TAX_ACCUMULATION_GOLDEN.read_text(encoding="utf-8"))
    for scenario in scenarios:
        scenario_id = scenario["scenarioId"]
        result = calculate_tax_accumulation(scenario["input"])
        for field, expected in scenario["expected"].items():
            actual = result[field]
            if not math.isclose(float(expected), float(actual), rel_tol=0.0, abs_tol=MONEY_TOLERANCE):
                errors.append(f"{scenario_id}: {field}: expected {expected}, reference {actual}")
    return len(scenarios)


def main() -> int:
    errors: list[str] = []
    base_count = verify_base(errors)
    resource_count = verify_resources(errors)
    tax_count = verify_tax_primitives(errors)
    tax_accumulation_count = verify_tax_accumulation(errors)
    if errors:
        print(f"Reference verification failed with {len(errors)} difference(s):", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(
        f"Reference verification passed: {base_count} base scenarios + "
        f"{resource_count} additional-resource scenarios + {tax_count} tax primitives + "
        f"{tax_accumulation_count} tax-accumulation scenarios; "
        f"tolerance EUR {MONEY_TOLERANCE:.2f}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
