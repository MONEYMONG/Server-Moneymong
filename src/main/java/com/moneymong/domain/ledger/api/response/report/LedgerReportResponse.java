package com.moneymong.domain.ledger.api.response.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record LedgerReportResponse(
        Long agencyId,
        String agencyName,
        PeriodInfo period,
        Integer totalIncome,
        Integer totalExpense,
        Integer totalBalance,
        List<MonthlyReport> monthly,
        List<MemberReport> members,
        List<CategoryReport> categories
) {
    @Builder
    public record PeriodInfo(
            Integer startYear,
            Integer startMonth,
            Integer endYear,
            Integer endMonth
    ) {
    }

    @Builder
    public record MonthlyReport(
            Integer year,
            Integer month,
            Integer income,
            Integer expense,
            Integer netAmount,
            Double incomeShareOfPeriod,
            Double expenseShareOfPeriod
    ) {
    }

    @Builder
    public record MemberReport(
            Long userId,
            String nickname,
            Integer income,
            Integer expense,
            Double incomeShare,
            Double expenseShare
    ) {
    }

    @Builder
    public record CategoryReport(
            String name,
            Integer income,
            Integer expense,
            Double share
    ) {
    }
}

