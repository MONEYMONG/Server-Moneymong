package com.moneymong.domain.ledger.service.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.repository.AgencyRepository;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.ledger.api.response.report.LedgerReportResponse;
import com.moneymong.domain.ledger.api.response.report.LedgerReportResponse.CategoryReport;
import com.moneymong.domain.ledger.api.response.report.LedgerReportResponse.MemberReport;
import com.moneymong.domain.ledger.api.response.report.LedgerReportResponse.MonthlyReport;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.domain.user.entity.User;
import com.moneymong.global.exception.custom.BadRequestException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerReportReaderTest {

    private static final Long USER_ID = 1L;
    private static final Long AGENCY_ID = 10L;

    @Mock
    private AgencyRepository agencyRepository;

    @Mock
    private AgencyUserRepository agencyUserRepository;

    @Mock
    private LedgerDetailRepository ledgerDetailRepository;

    @InjectMocks
    private LedgerReportReader ledgerReportReader;

    @Test
    @DisplayName("소속이 존재하지 않으면 AGENCY_NOT_FOUND 예외가 발생한다.")
    void getReport_agencyNotFound() {
        // given
        given(agencyRepository.findById(AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReportReader.getReport(USER_ID, AGENCY_ID, 2024, 1, 2024, 3))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);
        then(agencyUserRepository).should(never()).findByUserIdAndAgencyId(anyLong(), anyLong());
        then(ledgerDetailRepository).should(never()).findAllByAgencyId(anyLong());
    }

    @Test
    @DisplayName("소속 회원이 아니면 AGENCY_NOT_FOUND 예외가 발생한다.")
    void getReport_agencyUserNotFound() {
        // given
        given(agencyRepository.findById(AGENCY_ID)).willReturn(Optional.of(agency()));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReportReader.getReport(USER_ID, AGENCY_ID, 2024, 1, 2024, 3))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);
        then(ledgerDetailRepository).should(never()).findAllByAgencyId(anyLong());
    }

    @Test
    @DisplayName("차단된 소속 회원이 리포트를 조회하면 BLOCKED_AGENCY_USER 예외가 발생한다.")
    void getReport_blockedUser() {
        // given
        Agency agency = agency();
        given(agencyRepository.findById(AGENCY_ID)).willReturn(Optional.of(agency));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(agency, AgencyUserRole.BLOCKED)));

        // when & then
        assertThatThrownBy(() -> ledgerReportReader.getReport(USER_ID, AGENCY_ID, 2024, 1, 2024, 3))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BLOCKED_AGENCY_USER);
        then(ledgerDetailRepository).should(never()).findAllByAgencyId(anyLong());
    }

    @Test
    @DisplayName("전체 합계는 장부 전체 기간으로, 월별 집계는 조회 기간 내 내역으로 계산한다.")
    void getReport_success() {
        // given
        Agency agency = agency();
        given(agencyRepository.findById(AGENCY_ID)).willReturn(Optional.of(agency));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(agency, AgencyUserRole.MEMBER)));

        User user1 = user(1L, "김머니");
        User user2 = user(2L, "이몽");
        Category dues = category("회비");
        Category food = category("식비");

        List<LedgerDetail> details = List.of(
                // 조회 기간 이전 (2024-01)
                detail(user1, FundType.INCOME, 1_000, date(2024, 1, 15, 12, 0, 0), dues),
                // 시작 경계 (2024-02-01 00:00:00)
                detail(user1, FundType.INCOME, 3_000, date(2024, 2, 1, 0, 0, 0), dues),
                detail(user2, FundType.EXPENSE, 1_000, date(2024, 2, 10, 9, 0, 0), food),
                detail(user1, FundType.EXPENSE, 500, date(2024, 2, 20, 18, 0, 0), null),
                // 종료 경계 (2024-04-30 23:59:59)
                detail(user2, FundType.INCOME, 1_000, date(2024, 4, 30, 23, 59, 59), dues),
                // 조회 기간 이후 (2024-05-01 00:00:00)
                detail(user2, FundType.EXPENSE, 1_500, date(2024, 5, 1, 0, 0, 0), food)
        );
        given(ledgerDetailRepository.findAllByAgencyId(AGENCY_ID)).willReturn(details);

        // when
        LedgerReportResponse response = ledgerReportReader.getReport(USER_ID, AGENCY_ID, 2024, 2, 2024, 4);

        // then
        assertThat(response.agencyId()).isEqualTo(AGENCY_ID);
        assertThat(response.agencyName()).isEqualTo("머니몽");
        assertThat(response.period().startYear()).isEqualTo(2024);
        assertThat(response.period().startMonth()).isEqualTo(2);
        assertThat(response.period().endYear()).isEqualTo(2024);
        assertThat(response.period().endMonth()).isEqualTo(4);

        // 전체 합계는 기간 외 내역까지 포함한다.
        assertThat(response.totalIncome()).isEqualTo(5_000);
        assertThat(response.totalExpense()).isEqualTo(3_000);
        assertThat(response.totalBalance()).isEqualTo(2_000);

        assertThat(response.monthly())
                .extracting(MonthlyReport::year, MonthlyReport::month, MonthlyReport::income,
                        MonthlyReport::expense, MonthlyReport::netAmount)
                .containsExactly(
                        tuple(2024, 2, 3_000, 1_500, 1_500),
                        tuple(2024, 3, 0, 0, 0),
                        tuple(2024, 4, 1_000, 0, 1_000)
                );

        // 2월: 비율 분모는 전체 합계
        MonthlyReport february = response.monthly().get(0);
        assertThat(february.incomeShareOfPeriod()).isCloseTo(0.6, within(1e-9));
        assertThat(february.expenseShareOfPeriod()).isCloseTo(0.5, within(1e-9));

        // 2월 멤버: userId 오름차순, 비율 분모는 해당 월 합계
        assertThat(february.members())
                .extracting(MemberReport::userId, MemberReport::nickname, MemberReport::income, MemberReport::expense)
                .containsExactly(
                        tuple(1L, "김머니", 3_000, 500),
                        tuple(2L, "이몽", 0, 1_000)
                );
        assertThat(february.members().get(0).incomeShare()).isCloseTo(1.0, within(1e-9));
        assertThat(february.members().get(0).expenseShare()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(february.members().get(1).incomeShare()).isCloseTo(0.0, within(1e-9));
        assertThat(february.members().get(1).expenseShare()).isCloseTo(2.0 / 3, within(1e-9));

        // 2월 카테고리: 이름 오름차순, 카테고리 미지정 내역은 "카테고리 없음"으로 묶는다.
        assertThat(february.categories())
                .extracting(CategoryReport::name, CategoryReport::income, CategoryReport::expense)
                .containsExactly(
                        tuple("식비", 0, 1_000),
                        tuple("카테고리 없음", 0, 500),
                        tuple("회비", 3_000, 0)
                );
        assertThat(february.categories())
                .extracting(CategoryReport::incomeShare)
                .usingComparatorForType((a, b) -> Math.abs(a - b) < 1e-9 ? 0 : Double.compare(a, b), Double.class)
                .containsExactly(0.0, 0.0, 1.0);
        assertThat(february.categories())
                .extracting(CategoryReport::expenseShare)
                .usingComparatorForType((a, b) -> Math.abs(a - b) < 1e-9 ? 0 : Double.compare(a, b), Double.class)
                .containsExactly(2.0 / 3, 1.0 / 3, 0.0);

        // 3월: 내역이 없으면 0과 빈 목록
        MonthlyReport march = response.monthly().get(1);
        assertThat(march.incomeShareOfPeriod()).isZero();
        assertThat(march.expenseShareOfPeriod()).isZero();
        assertThat(march.members()).isEmpty();
        assertThat(march.categories()).isEmpty();

        // 4월: 지출이 없는 달의 지출 비율은 0
        MonthlyReport april = response.monthly().get(2);
        assertThat(april.incomeShareOfPeriod()).isCloseTo(0.2, within(1e-9));
        assertThat(april.expenseShareOfPeriod()).isZero();
        assertThat(april.members())
                .extracting(MemberReport::userId, MemberReport::income, MemberReport::expense,
                        MemberReport::incomeShare, MemberReport::expenseShare)
                .containsExactly(tuple(2L, 1_000, 0, 1.0, 0.0));
        assertThat(april.categories())
                .extracting(CategoryReport::name, CategoryReport::income, CategoryReport::expense,
                        CategoryReport::incomeShare, CategoryReport::expenseShare)
                .containsExactly(tuple("회비", 1_000, 0, 1.0, 0.0));
    }

    @Test
    @DisplayName("장부 내역이 없으면 합계와 비율이 모두 0이고 연도를 넘는 기간도 월별로 채워진다.")
    void getReport_emptyDetailsAcrossYear() {
        // given
        Agency agency = agency();
        given(agencyRepository.findById(AGENCY_ID)).willReturn(Optional.of(agency));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(agency, AgencyUserRole.STAFF)));
        given(ledgerDetailRepository.findAllByAgencyId(AGENCY_ID)).willReturn(Collections.emptyList());

        // when
        LedgerReportResponse response = ledgerReportReader.getReport(USER_ID, AGENCY_ID, 2023, 11, 2024, 2);

        // then
        assertThat(response.totalIncome()).isZero();
        assertThat(response.totalExpense()).isZero();
        assertThat(response.totalBalance()).isZero();
        assertThat(response.monthly())
                .extracting(MonthlyReport::year, MonthlyReport::month, MonthlyReport::income,
                        MonthlyReport::expense, MonthlyReport::incomeShareOfPeriod, MonthlyReport::expenseShareOfPeriod)
                .containsExactly(
                        tuple(2023, 11, 0, 0, 0.0, 0.0),
                        tuple(2023, 12, 0, 0, 0.0, 0.0),
                        tuple(2024, 1, 0, 0, 0.0, 0.0),
                        tuple(2024, 2, 0, 0, 0.0, 0.0)
                );
        assertThat(response.monthly()).allSatisfy(monthly -> {
            assertThat(monthly.members()).isEmpty();
            assertThat(monthly.categories()).isEmpty();
        });
    }

    private Agency agency() {
        return Agency.builder()
                .id(AGENCY_ID)
                .agencyName("머니몽")
                .build();
    }

    private AgencyUser agencyUser(Agency agency, AgencyUserRole role) {
        return AgencyUser.builder()
                .id(100L)
                .agency(agency)
                .userId(USER_ID)
                .agencyUserRole(role)
                .build();
    }

    private User user(Long id, String nickname) {
        return User.builder()
                .id(id)
                .nickname(nickname)
                .build();
    }

    private Category category(String name) {
        return Category.builder()
                .name(name)
                .agencyId(AGENCY_ID)
                .build();
    }

    private ZonedDateTime date(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneId.systemDefault());
    }

    private LedgerDetail detail(User user, FundType fundType, int amount, ZonedDateTime paymentDate,
                                Category category) {
        return LedgerDetail.builder()
                .user(user)
                .fundType(fundType)
                .amount(amount)
                .paymentDate(paymentDate)
                .category(category)
                .build();
    }
}
