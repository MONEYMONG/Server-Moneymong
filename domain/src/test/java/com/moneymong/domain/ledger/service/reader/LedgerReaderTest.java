package com.moneymong.domain.ledger.service.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.ledger.service.dto.response.ledger.LedgerInfoView;
import com.moneymong.domain.ledger.service.dto.response.ledger.LedgerInfoViewDetail;
import com.moneymong.domain.ledger.service.dto.response.ledger.LedgerInfoViewV2;
import com.moneymong.domain.ledger.entity.Ledger;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.domain.ledger.repository.LedgerRepository;
import com.moneymong.domain.user.entity.User;
import com.moneymong.domain.user.repository.UserRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class LedgerReaderTest {

    private static final long USER_ID = 1L;
    private static final long LEDGER_ID = 20L;
    private static final long AGENCY_ID = 10L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AgencyUserRepository agencyUserRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private LedgerDetailRepository ledgerDetailRepository;

    @InjectMocks
    private LedgerReader ledgerReader;

    // === searchLedgersByPeriod ===

    @Test
    @DisplayName("기간별 장부 조회 시 내역 순번을 역순으로 부여하고 전체 건수와 소속 이름을 함께 반환한다.")
    void searchLedgersByPeriod_success() {
        // given
        Ledger ledger = ledger();
        givenLedgerAndAgencyUser(ledger, AgencyUserRole.MEMBER);
        List<LedgerDetail> details = List.of(
                detail(101L, "편의점", FundType.EXPENSE, 3_000, 7_000),
                detail(102L, "회비", FundType.INCOME, 10_000, 10_000)
        );
        ZonedDateTime from = monthStart(2024, 1);
        ZonedDateTime to = monthStart(2024, 3);
        given(ledgerDetailRepository.searchByPeriod(ledger, from, to, PageRequest.of(0, 2)))
                .willReturn(new PageImpl<>(details, PageRequest.of(0, 2), 5));

        // when
        LedgerInfoViewV2 result = ledgerReader.searchLedgersByPeriod(USER_ID, LEDGER_ID, 2024, 1, 2024, 3, 0, 2);

        // then
        assertThat(result.getId()).isEqualTo(LEDGER_ID);
        assertThat(result.getTotalBalance()).isEqualTo(7_000);
        assertThat(result.getLedgerDetailTotalCount()).isEqualTo(5L);
        assertThat(result.getAgencyName()).isEqualTo("머니몽");
        assertThat(result.getLedgerInfoViewDetails())
                .extracting(LedgerInfoViewDetail::getId, LedgerInfoViewDetail::getStoreInfo,
                        LedgerInfoViewDetail::getFundType, LedgerInfoViewDetail::getAmount,
                        LedgerInfoViewDetail::getBalance, LedgerInfoViewDetail::getOrder)
                .containsExactly(
                        tuple(101L, "편의점", FundType.EXPENSE, 3_000, 7_000, 2),
                        tuple(102L, "회비", FundType.INCOME, 10_000, 10_000, 1)
                );
    }

    @Test
    @DisplayName("기간별 장부 조회 시 장부가 없으면 LEDGER_NOT_FOUND 예외가 발생한다.")
    void searchLedgersByPeriod_ledgerNotFound() {
        // given
        given(ledgerRepository.findById(LEDGER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReader.searchLedgersByPeriod(USER_ID, LEDGER_ID, 2024, 1, 2024, 3, 0, 10))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_NOT_FOUND);
    }

    @Test
    @DisplayName("기간별 장부 조회 시 소속 회원이 아니면 AGENCY_NOT_FOUND 예외가 발생한다.")
    void searchLedgersByPeriod_agencyUserNotFound() {
        // given
        given(ledgerRepository.findById(LEDGER_ID)).willReturn(Optional.of(ledger()));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReader.searchLedgersByPeriod(USER_ID, LEDGER_ID, 2024, 1, 2024, 3, 0, 10))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);
    }

    @Test
    @DisplayName("기간별 장부 조회 시 차단된 회원이면 BLOCKED_AGENCY_USER 예외가 발생한다.")
    void searchLedgersByPeriod_blocked() {
        // given
        givenLedgerAndAgencyUser(ledger(), AgencyUserRole.BLOCKED);

        // when & then
        assertThatThrownBy(() -> ledgerReader.searchLedgersByPeriod(USER_ID, LEDGER_ID, 2024, 1, 2024, 3, 0, 10))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BLOCKED_AGENCY_USER);
        then(ledgerDetailRepository).should(never()).searchByPeriod(any(), any(), any(), any());
    }

    // === searchLedgersByPeriodAndFundType ===

    @Test
    @DisplayName("기간과 자금 유형으로 장부를 조회하면 해당 유형 내역과 전체 건수를 반환한다.")
    void searchLedgersByPeriodAndFundType_success() {
        // given
        Ledger ledger = ledger();
        givenLedgerAndAgencyUser(ledger, AgencyUserRole.STAFF);
        List<LedgerDetail> details = List.of(detail(201L, "회비", FundType.INCOME, 10_000, 10_000));
        given(ledgerDetailRepository.searchByPeriodAndFundType(
                ledger, monthStart(2023, 12), monthStart(2024, 2), FundType.INCOME, PageRequest.of(1, 1)))
                .willReturn(new PageImpl<>(details, PageRequest.of(1, 1), 3));

        // when
        LedgerInfoViewV2 result = ledgerReader.searchLedgersByPeriodAndFundType(
                USER_ID, LEDGER_ID, 2023, 12, 2024, 2, 1, 1, FundType.INCOME);

        // then
        assertThat(result.getLedgerDetailTotalCount()).isEqualTo(3L);
        assertThat(result.getAgencyName()).isEqualTo("머니몽");
        assertThat(result.getLedgerInfoViewDetails())
                .extracting(LedgerInfoViewDetail::getId, LedgerInfoViewDetail::getFundType, LedgerInfoViewDetail::getOrder)
                .containsExactly(tuple(201L, FundType.INCOME, 1));
    }

    @Test
    @DisplayName("기간과 자금 유형으로 조회 시 차단된 회원이면 BLOCKED_AGENCY_USER 예외가 발생한다.")
    void searchLedgersByPeriodAndFundType_blocked() {
        // given
        givenLedgerAndAgencyUser(ledger(), AgencyUserRole.BLOCKED);

        // when & then
        assertThatThrownBy(() -> ledgerReader.searchLedgersByPeriodAndFundType(
                USER_ID, LEDGER_ID, 2024, 1, 2024, 3, 0, 10, FundType.EXPENSE))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BLOCKED_AGENCY_USER);
        then(ledgerDetailRepository).should(never()).searchByPeriodAndFundType(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("기간과 자금 유형으로 조회 시 장부가 없으면 LEDGER_NOT_FOUND 예외가 발생한다.")
    void searchLedgersByPeriodAndFundType_ledgerNotFound() {
        // given
        given(ledgerRepository.findById(LEDGER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReader.searchLedgersByPeriodAndFundType(
                USER_ID, LEDGER_ID, 2024, 1, 2024, 3, 0, 10, FundType.EXPENSE))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_NOT_FOUND);
    }

    // === search ===

    @Test
    @DisplayName("월별 장부 조회 시 해당 월 1일부터 다음 달 1일까지의 내역을 조회한다.")
    void search_success() {
        // given
        Ledger ledger = ledger();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        givenLedgerAndAgencyUser(ledger, AgencyUserRole.MEMBER);
        List<LedgerDetail> details = List.of(
                detail(301L, "문구점", FundType.EXPENSE, 2_000, 5_000),
                detail(302L, "식당", FundType.EXPENSE, 3_000, 7_000),
                detail(303L, "회비", FundType.INCOME, 10_000, 10_000)
        );
        given(ledgerDetailRepository.search(ledger, monthStart(2024, 12), monthStart(2025, 1), PageRequest.of(0, 20)))
                .willReturn(details);

        // when
        LedgerInfoView result = ledgerReader.search(USER_ID, LEDGER_ID, 2024, 12, 0, 20);

        // then
        assertThat(result.getId()).isEqualTo(LEDGER_ID);
        assertThat(result.getTotalBalance()).isEqualTo(7_000);
        assertThat(result.getLedgerInfoViewDetails())
                .extracting(LedgerInfoViewDetail::getId, LedgerInfoViewDetail::getOrder)
                .containsExactly(tuple(301L, 3), tuple(302L, 2), tuple(303L, 1));
    }

    @Test
    @DisplayName("월별 장부 조회 결과가 없으면 빈 목록을 반환한다.")
    void search_empty() {
        // given
        Ledger ledger = ledger();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        givenLedgerAndAgencyUser(ledger, AgencyUserRole.MEMBER);
        given(ledgerDetailRepository.search(ledger, monthStart(2024, 5), monthStart(2024, 6), PageRequest.of(0, 20)))
                .willReturn(Collections.emptyList());

        // when
        LedgerInfoView result = ledgerReader.search(USER_ID, LEDGER_ID, 2024, 5, 0, 20);

        // then
        assertThat(result.getLedgerInfoViewDetails()).isEmpty();
    }

    @Test
    @DisplayName("월별 장부 조회 시 유저가 없으면 USER_NOT_FOUND 예외가 발생한다.")
    void search_userNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReader.search(USER_ID, LEDGER_ID, 2024, 5, 0, 20))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        then(ledgerRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("월별 장부 조회 시 차단된 회원이면 BLOCKED_AGENCY_USER 예외가 발생한다.")
    void search_blocked() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        givenLedgerAndAgencyUser(ledger(), AgencyUserRole.BLOCKED);

        // when & then
        assertThatThrownBy(() -> ledgerReader.search(USER_ID, LEDGER_ID, 2024, 5, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BLOCKED_AGENCY_USER);
    }

    // === searchByFilter ===

    @Test
    @DisplayName("자금 유형 필터로 월별 장부를 조회하면 해당 유형 내역을 반환한다.")
    void searchByFilter_success() {
        // given
        Ledger ledger = ledger();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        givenLedgerAndAgencyUser(ledger, AgencyUserRole.STAFF);
        List<LedgerDetail> details = List.of(detail(401L, "식당", FundType.EXPENSE, 3_000, 7_000));
        given(ledgerDetailRepository.searchByFundType(
                ledger, monthStart(2024, 3), monthStart(2024, 4), FundType.EXPENSE, PageRequest.of(0, 10)))
                .willReturn(details);

        // when
        LedgerInfoView result = ledgerReader.searchByFilter(USER_ID, LEDGER_ID, 2024, 3, 0, 10, FundType.EXPENSE);

        // then
        assertThat(result.getLedgerInfoViewDetails())
                .extracting(LedgerInfoViewDetail::getId, LedgerInfoViewDetail::getFundType,
                        LedgerInfoViewDetail::getAmount, LedgerInfoViewDetail::getOrder)
                .containsExactly(tuple(401L, FundType.EXPENSE, 3_000, 1));
    }

    @Test
    @DisplayName("자금 유형 필터 조회 시 소속 회원이 아니면 AGENCY_NOT_FOUND 예외가 발생한다.")
    void searchByFilter_agencyUserNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerRepository.findById(LEDGER_ID)).willReturn(Optional.of(ledger()));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReader.searchByFilter(USER_ID, LEDGER_ID, 2024, 3, 0, 10, FundType.EXPENSE))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);
    }

    @Test
    @DisplayName("자금 유형 필터 조회 시 차단된 회원이면 BLOCKED_AGENCY_USER 예외가 발생한다.")
    void searchByFilter_blocked() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        givenLedgerAndAgencyUser(ledger(), AgencyUserRole.BLOCKED);

        // when & then
        assertThatThrownBy(() -> ledgerReader.searchByFilter(USER_ID, LEDGER_ID, 2024, 3, 0, 10, FundType.INCOME))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BLOCKED_AGENCY_USER);
        then(ledgerDetailRepository).should(never()).searchByFundType(any(), any(), any(), any(), any());
    }

    // === searchByAgency ===

    @Test
    @DisplayName("소속 ID로 장부를 조회하면 해당 소속의 장부 내역을 반환한다.")
    void searchByAgency_success() {
        // given
        Ledger ledger = ledger();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.of(ledger));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(ledger.getAgency(), AgencyUserRole.MEMBER)));
        given(ledgerDetailRepository.search(ledger, monthStart(2024, 7), monthStart(2024, 8), PageRequest.of(0, 10)))
                .willReturn(List.of(detail(501L, "회비", FundType.INCOME, 10_000, 10_000)));

        // when
        LedgerInfoView result = ledgerReader.searchByAgency(USER_ID, AGENCY_ID, 2024, 7, 0, 10);

        // then
        assertThat(result.getId()).isEqualTo(LEDGER_ID);
        assertThat(result.getLedgerInfoViewDetails())
                .extracting(LedgerInfoViewDetail::getId, LedgerInfoViewDetail::getOrder)
                .containsExactly(tuple(501L, 1));
    }

    @Test
    @DisplayName("소속 ID로 장부 조회 시 장부가 없으면 LEDGER_NOT_FOUND 예외가 발생한다.")
    void searchByAgency_ledgerNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReader.searchByAgency(USER_ID, AGENCY_ID, 2024, 7, 0, 10))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_NOT_FOUND);
    }

    @Test
    @DisplayName("소속 ID로 장부 조회 시 소속 회원이 아니면 AGENCY_NOT_FOUND 예외가 발생한다.")
    void searchByAgency_agencyUserNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.of(ledger()));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReader.searchByAgency(USER_ID, AGENCY_ID, 2024, 7, 0, 10))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);
    }

    @Test
    @DisplayName("소속 ID로 장부 조회 시 차단된 회원이면 BLOCKED_AGENCY_USER 예외가 발생한다.")
    void searchByAgency_blocked() {
        // given
        Ledger ledger = ledger();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.of(ledger));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(ledger.getAgency(), AgencyUserRole.BLOCKED)));

        // when & then
        assertThatThrownBy(() -> ledgerReader.searchByAgency(USER_ID, AGENCY_ID, 2024, 7, 0, 10))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BLOCKED_AGENCY_USER);
    }

    // === exists ===

    @Test
    @DisplayName("소속 장부에 내역이 있으면 true를 반환한다.")
    void exists_true() {
        // given
        Ledger ledger = ledger();
        given(ledgerRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.of(ledger));
        given(ledgerDetailRepository.existsByLedger(ledger)).willReturn(true);

        // when & then
        assertThat(ledgerReader.exists(AGENCY_ID)).isTrue();
    }

    @Test
    @DisplayName("소속 장부에 내역이 없으면 false를 반환한다.")
    void exists_false() {
        // given
        Ledger ledger = ledger();
        given(ledgerRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.of(ledger));
        given(ledgerDetailRepository.existsByLedger(ledger)).willReturn(false);

        // when & then
        assertThat(ledgerReader.exists(AGENCY_ID)).isFalse();
    }

    @Test
    @DisplayName("내역 존재 여부 확인 시 장부가 없으면 LEDGER_NOT_FOUND 예외가 발생한다.")
    void exists_ledgerNotFound() {
        // given
        given(ledgerRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReader.exists(AGENCY_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_NOT_FOUND);
    }

    private void givenLedgerAndAgencyUser(Ledger ledger, AgencyUserRole role) {
        given(ledgerRepository.findById(LEDGER_ID)).willReturn(Optional.of(ledger));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(ledger.getAgency(), role)));
    }

    private Ledger ledger() {
        Agency agency = Agency.builder()
                .id(AGENCY_ID)
                .agencyName("머니몽")
                .build();
        return Ledger.builder()
                .id(LEDGER_ID)
                .agency(agency)
                .totalBalance(7_000)
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

    private User user() {
        return User.builder()
                .id(USER_ID)
                .nickname("김머니")
                .build();
    }

    private ZonedDateTime monthStart(int year, int month) {
        return ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.systemDefault());
    }

    private LedgerDetail detail(Long id, String storeInfo, FundType fundType, int amount, int balance) {
        return LedgerDetail.builder()
                .id(id)
                .storeInfo(storeInfo)
                .fundType(fundType)
                .amount(amount)
                .balance(balance)
                .paymentDate(monthStart(2024, 1))
                .build();
    }
}
