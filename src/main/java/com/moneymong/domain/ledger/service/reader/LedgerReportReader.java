package com.moneymong.domain.ledger.service.reader;

import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.repository.AgencyRepository;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.ledger.api.response.report.LedgerReportResponse;
import com.moneymong.domain.ledger.api.response.report.LedgerReportResponse.CategoryReport;
import com.moneymong.domain.ledger.api.response.report.LedgerReportResponse.MemberReport;
import com.moneymong.domain.ledger.api.response.report.LedgerReportResponse.MonthlyReport;
import com.moneymong.domain.ledger.api.response.report.LedgerReportResponse.PeriodInfo;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.global.exception.custom.BadRequestException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.moneymong.domain.agency.entity.enums.AgencyUserRole.isBlockedUser;
import static java.time.ZoneId.systemDefault;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class LedgerReportReader {
    private final AgencyRepository agencyRepository;
    private final AgencyUserRepository agencyUserRepository;
    private final LedgerDetailRepository ledgerDetailRepository;

    public LedgerReportResponse getReport(
            Long userId,
            Long agencyId,
            Integer startYear,
            Integer startMonth,
            Integer endYear,
            Integer endMonth
    ) {
        // 권한 검증
        Agency agency = getAgency(agencyId);
        AgencyUser agencyUser = getAgencyUser(userId, agencyId);
        ensureAgencyUserNotBlocked(agencyUser.getAgencyUserRole());

        // 해당 agency의 모든 ledger details 조회
        List<LedgerDetail> allDetails = ledgerDetailRepository.findAllByAgencyId(agencyId);

        // 장부 전체 기간 합계 (응답의 totalIncome/totalExpense/totalBalance, 비율 계산 분모)
        int totalIncome = calculateTotalByFundType(allDetails, FundType.INCOME);
        int totalExpense = calculateTotalByFundType(allDetails, FundType.EXPENSE);
        int totalBalance = totalIncome - totalExpense;

        // 기간 필터링
        List<LedgerDetail> filteredDetails = filterByPeriod(allDetails, startYear, startMonth, endYear, endMonth);

        // 월별 집계 (각 month 안에 members/categories 포함)
        List<MonthlyReport> monthlyReports = generateMonthlyReports(
                filteredDetails,
                startYear, startMonth,
                endYear, endMonth,
                totalIncome,
                totalExpense
        );

        return LedgerReportResponse.builder()
                .agencyId(agencyId)
                .agencyName(agency.getAgencyName())
                .period(PeriodInfo.builder()
                        .startYear(startYear)
                        .startMonth(startMonth)
                        .endYear(endYear)
                        .endMonth(endMonth)
                        .build())
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .totalBalance(totalBalance)
                .monthly(monthlyReports)
                .build();
    }

    private Agency getAgency(Long agencyId) {
        return agencyRepository.findById(agencyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENCY_NOT_FOUND));
    }

    private AgencyUser getAgencyUser(Long userId, Long agencyId) {
        return agencyUserRepository.findByUserIdAndAgencyId(userId, agencyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENCY_NOT_FOUND));
    }

    private void ensureAgencyUserNotBlocked(com.moneymong.domain.agency.entity.enums.AgencyUserRole role) {
        if (isBlockedUser(role)) {
            throw new BadRequestException(ErrorCode.BLOCKED_AGENCY_USER);
        }
    }

    private List<LedgerDetail> filterByPeriod(
            List<LedgerDetail> details,
            int startYear, int startMonth,
            int endYear, int endMonth
    ) {
        ZonedDateTime startDate = ZonedDateTime.of(startYear, startMonth, 1, 0, 0, 0, 0, 
                systemDefault());
        ZonedDateTime endDate = ZonedDateTime.of(endYear, endMonth, 1, 0, 0, 0, 0,
                systemDefault()).plusMonths(1).minusNanos(1);

        return details.stream()
                .filter(detail -> {
                    ZonedDateTime paymentDate = detail.getPaymentDate();
                    return !paymentDate.isBefore(startDate) && !paymentDate.isAfter(endDate);
                })
                .collect(Collectors.toList());
    }

    private int calculateTotalByFundType(List<LedgerDetail> details, FundType fundType) {
        return details.stream()
                .filter(detail -> detail.getFundType() == fundType)
                .mapToInt(LedgerDetail::getAmount)
                .sum();
    }

    private List<MonthlyReport> generateMonthlyReports(
            List<LedgerDetail> details,
            int startYear, int startMonth,
            int endYear, int endMonth,
            int totalIncome,
            int totalExpense
    ) {
        // 월별로 그룹화
        Map<String, List<LedgerDetail>> monthlyGrouped = details.stream()
                .collect(Collectors.groupingBy(detail -> {
                    ZonedDateTime date = detail.getPaymentDate();
                    return date.getYear() + "-" + date.getMonthValue();
                }));

        List<MonthlyReport> reports = new ArrayList<>();

        // 시작 월부터 종료 월까지 순회
        ZonedDateTime current = ZonedDateTime.of(startYear, startMonth, 1, 0, 0, 0, 0,
                systemDefault());
        ZonedDateTime end = ZonedDateTime.of(endYear, endMonth, 1, 0, 0, 0, 0,
                systemDefault());

        while (!current.isAfter(end)) {
            int year = current.getYear();
            int month = current.getMonthValue();
            String key = year + "-" + month;

            List<LedgerDetail> monthDetails = monthlyGrouped.getOrDefault(key, Collections.emptyList());

            int monthIncome = calculateTotalByFundType(monthDetails, FundType.INCOME);
            int monthExpense = calculateTotalByFundType(monthDetails, FundType.EXPENSE);
            int netAmount = monthIncome - monthExpense;

            double incomeShare = totalIncome > 0 ? (double) monthIncome / totalIncome : 0.0;
            double expenseShare = totalExpense > 0 ? (double) monthExpense / totalExpense : 0.0;

            // 월별 멤버/카테고리 집계 (share 분모: 해당 월 합계)
            List<MemberReport> monthMembers = generateMemberReports(monthDetails, monthIncome, monthExpense);
            List<CategoryReport> monthCategories = generateCategoryReports(monthDetails, monthIncome, monthExpense);

            reports.add(MonthlyReport.builder()
                    .year(year)
                    .month(month)
                    .income(monthIncome)
                    .expense(monthExpense)
                    .netAmount(netAmount)
                    .incomeShareOfPeriod(incomeShare)
                    .expenseShareOfPeriod(expenseShare)
                    .members(monthMembers)
                    .categories(monthCategories)
                    .build());

            current = current.plusMonths(1);
        }

        return reports;
    }

    private List<MemberReport> generateMemberReports(
            List<LedgerDetail> details,
            int totalIncome,
            int totalExpense
    ) {
        // 유저별로 그룹화
        Map<Long, List<LedgerDetail>> userGrouped = details.stream()
                .collect(Collectors.groupingBy(detail -> detail.getUser().getId()));

        return userGrouped.entrySet().stream()
                .map(entry -> {
                    Long userId = entry.getKey();
                    List<LedgerDetail> userDetails = entry.getValue();

                    int userIncome = calculateTotalByFundType(userDetails, FundType.INCOME);
                    int userExpense = calculateTotalByFundType(userDetails, FundType.EXPENSE);

                    double incomeShare = totalIncome > 0 ? (double) userIncome / totalIncome : 0.0;
                    double expenseShare = totalExpense > 0 ? (double) userExpense / totalExpense : 0.0;

                    String nickname = userDetails.get(0).getUser().getNickname();

                    return MemberReport.builder()
                            .userId(userId)
                            .nickname(nickname)
                            .income(userIncome)
                            .expense(userExpense)
                            .incomeShare(incomeShare)
                            .expenseShare(expenseShare)
                            .build();
                })
                .sorted(Comparator.comparing(MemberReport::userId))
                .collect(Collectors.toList());
    }

    private List<CategoryReport> generateCategoryReports(
            List<LedgerDetail> details,
            int totalIncome,
            int totalExpense
    ) {
        Map<String, Integer> incomeByCategory = new HashMap<>();
        Map<String, Integer> expenseByCategory = new HashMap<>();

        for (LedgerDetail detail : details) {
            String categoryName = detail.getCategory() != null
                    ? detail.getCategory().getName()
                    : "카테고리 없음";

            if (detail.getFundType() == FundType.INCOME) {
                incomeByCategory.merge(categoryName, detail.getAmount(), Integer::sum);
            } else if (detail.getFundType() == FundType.EXPENSE) {
                expenseByCategory.merge(categoryName, detail.getAmount(), Integer::sum);
            }
        }

        Set<String> allCategories = new HashSet<>();
        allCategories.addAll(incomeByCategory.keySet());
        allCategories.addAll(expenseByCategory.keySet());

        return allCategories.stream()
                .map(categoryName -> {
                    int income = incomeByCategory.getOrDefault(categoryName, 0);
                    int expense = expenseByCategory.getOrDefault(categoryName, 0);

                    double incomeShare = totalIncome > 0 ? (double) income / totalIncome : 0.0;
                    double expenseShare = totalExpense > 0 ? (double) expense / totalExpense : 0.0;

                    return CategoryReport.builder()
                            .name(categoryName)
                            .income(income)
                            .expense(expense)
                            .incomeShare(incomeShare)
                            .expenseShare(expenseShare)
                            .build();
                })
                .sorted(Comparator.comparing(CategoryReport::name))
                .collect(Collectors.toList());
    }
}

