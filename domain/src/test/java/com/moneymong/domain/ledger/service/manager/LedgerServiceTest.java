package com.moneymong.domain.ledger.service.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.category.repository.CategoryRepository;
import com.moneymong.domain.ledger.service.dto.request.CreateLedgerRequest;
import com.moneymong.domain.ledger.service.dto.request.CreateLedgerRequestV2;
import com.moneymong.domain.ledger.service.dto.request.UpdateLedgerRequest;
import com.moneymong.domain.ledger.service.dto.response.LedgerDetailInfoView;
import com.moneymong.domain.ledger.service.dto.response.LedgerDetailInfoViewV2;
import com.moneymong.domain.ledger.service.dto.response.LedgerDocumentInfoView;
import com.moneymong.domain.ledger.service.dto.response.LedgerReceiptInfoView;
import com.moneymong.domain.ledger.entity.Ledger;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.entity.LedgerDocument;
import com.moneymong.domain.ledger.entity.LedgerReceipt;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.domain.ledger.repository.LedgerRepository;
import com.moneymong.domain.user.entity.User;
import com.moneymong.domain.user.repository.UserRepository;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long AGENCY_ID = 10L;
    private static final Long LEDGER_ID = 1L;
    private static final Long DETAIL_ID = 1000L;
    private static final ZonedDateTime PAYMENT_DATE = ZonedDateTime.of(2024, 3, 1, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"));

    @Mock
    private LedgerDetailService ledgerDetailService;
    @Mock
    private LedgerReceiptManager ledgerReceiptManager;
    @Mock
    private LedgerDocumentManager ledgerDocumentManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AgencyUserRepository agencyUserRepository;
    @Mock
    private LedgerRepository ledgerRepository;
    @Mock
    private LedgerDetailRepository ledgerDetailRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    // ===== createLedger (V1) =====

    @Test
    @DisplayName("운영진이 영수증과 증빙 자료를 포함해 장부 내역을 등록하면 현재 총 잔액 기준으로 등록되고 첨부가 함께 반환된다.")
    void createLedger_withAttachments() {
        // given
        User user = user();
        Ledger ledger = ledger(50_000);
        LedgerDetail created = detail(ledger, user, FundType.EXPENSE, 12_000, null);
        List<String> receiptUrls = List.of("https://img/r1.png");
        List<String> documentUrls = List.of("https://img/d1.png");
        CreateLedgerRequest request = new CreateLedgerRequest(
                "문구점", FundType.EXPENSE, 12_000, "비품 구매", PAYMENT_DATE, receiptUrls, documentUrls);

        givenStaffContext(user, ledger, AgencyUserRole.STAFF);
        given(ledgerDetailService.createLedgerDetail(
                ledger, user, "문구점", FundType.EXPENSE, 12_000, 50_000, "비품 구매", PAYMENT_DATE, null))
                .willReturn(created);
        given(ledgerReceiptManager.createReceipts(DETAIL_ID, receiptUrls))
                .willReturn(List.of(LedgerReceipt.builder().id(11L).ledgerDetail(created).receiptImageUrl("https://img/r1.png").build()));
        given(ledgerDocumentManager.createLedgerDocuments(DETAIL_ID, documentUrls))
                .willReturn(List.of(LedgerDocument.builder().id(21L).ledgerDetail(created).documentImageUrl("https://img/d1.png").build()));

        // when
        LedgerDetailInfoView view = ledgerService.createLedger(USER_ID, LEDGER_ID, request);

        // then
        assertThat(view.getId()).isEqualTo(DETAIL_ID);
        assertThat(view.getAmount()).isEqualTo(12_000);
        assertThat(view.getFundType()).isEqualTo(FundType.EXPENSE);
        assertThat(view.getAuthorName()).isEqualTo("머니몽");
        assertThat(view.getReceiptImageUrls()).extracting(LedgerReceiptInfoView::getId, LedgerReceiptInfoView::getReceiptImageUrl)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(11L, "https://img/r1.png"));
        assertThat(view.getDocumentImageUrls()).extracting(LedgerDocumentInfoView::getId, LedgerDocumentInfoView::getDocumentImageUrl)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(21L, "https://img/d1.png"));
    }

    @Test
    @DisplayName("첨부 목록이 비어 있으면 영수증과 증빙 자료를 생성하지 않는다.")
    void createLedger_withoutAttachments() {
        // given
        User user = user();
        Ledger ledger = ledger(0);
        LedgerDetail created = detail(ledger, user, FundType.INCOME, 30_000, null);
        CreateLedgerRequest request = new CreateLedgerRequest(
                "회비", FundType.INCOME, 30_000, "3월 회비", PAYMENT_DATE, List.of(), List.of());

        givenStaffContext(user, ledger, AgencyUserRole.STAFF);
        given(ledgerDetailService.createLedgerDetail(
                ledger, user, "회비", FundType.INCOME, 30_000, 0, "3월 회비", PAYMENT_DATE, null))
                .willReturn(created);

        // when
        LedgerDetailInfoView view = ledgerService.createLedger(USER_ID, LEDGER_ID, request);

        // then
        assertThat(view.getReceiptImageUrls()).isEmpty();
        assertThat(view.getDocumentImageUrls()).isEmpty();
        then(ledgerReceiptManager).shouldHaveNoInteractions();
        then(ledgerDocumentManager).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("운영진이 아닌 회원이 장부 내역을 등록하면 INVALID_AGENCY_USER_ACCESS 예외가 발생한다.")
    void createLedger_notStaff() {
        // given
        User user = user();
        Ledger ledger = ledger(0);
        givenStaffContext(user, ledger, AgencyUserRole.MEMBER);
        CreateLedgerRequest request = new CreateLedgerRequest(
                "회비", FundType.INCOME, 30_000, "3월 회비", PAYMENT_DATE, List.of(), List.of());

        // when & then
        assertThatThrownBy(() -> ledgerService.createLedger(USER_ID, LEDGER_ID, request))
                .isInstanceOf(InvalidAccessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);

        then(ledgerDetailService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 유저가 장부 내역을 등록하면 USER_NOT_FOUND 예외가 발생한다.")
    void createLedger_userNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerService.createLedger(USER_ID, LEDGER_ID, null))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(ledgerRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 장부에 내역을 등록하면 LEDGER_NOT_FOUND 예외가 발생한다.")
    void createLedger_ledgerNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerRepository.findById(LEDGER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerService.createLedger(USER_ID, LEDGER_ID, null))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_NOT_FOUND);

        then(agencyUserRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("장부 소속에 가입하지 않은 유저가 내역을 등록하면 AGENCY_NOT_FOUND 예외가 발생한다.")
    void createLedger_notAgencyMember() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerRepository.findById(LEDGER_ID)).willReturn(Optional.of(ledger(0)));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerService.createLedger(USER_ID, LEDGER_ID, null))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);

        then(ledgerDetailService).shouldHaveNoInteractions();
    }

    // ===== createLedgerV2 =====

    @Test
    @DisplayName("V2 등록 시 카테고리를 소속 기준으로 조회해 내역에 연결하고 증빙 자료를 함께 반환한다.")
    void createLedgerV2_withCategory() {
        // given
        User user = user();
        Ledger ledger = ledger(20_000);
        Category category = Category.builder().id(5L).name("식비").agencyId(AGENCY_ID).build();
        LedgerDetail created = detail(ledger, user, FundType.EXPENSE, 8_000, category);
        List<String> documentUrls = List.of("https://img/d1.png", "https://img/d2.png");
        CreateLedgerRequestV2 request = new CreateLedgerRequestV2(
                "식당", FundType.EXPENSE, 8_000, "회식", PAYMENT_DATE, documentUrls, "식비");

        givenStaffContext(user, ledger, AgencyUserRole.STAFF);
        given(categoryRepository.findByAgencyIdAndName(AGENCY_ID, "식비")).willReturn(Optional.of(category));
        given(ledgerDetailService.createLedgerDetail(
                ledger, user, "식당", FundType.EXPENSE, 8_000, 20_000, "회식", PAYMENT_DATE, category))
                .willReturn(created);
        given(ledgerDocumentManager.createLedgerDocuments(DETAIL_ID, documentUrls)).willReturn(List.of(
                LedgerDocument.builder().id(21L).ledgerDetail(created).documentImageUrl("https://img/d1.png").build(),
                LedgerDocument.builder().id(22L).ledgerDetail(created).documentImageUrl("https://img/d2.png").build()));

        // when
        LedgerDetailInfoViewV2 view = ledgerService.createLedgerV2(USER_ID, LEDGER_ID, request);

        // then
        assertThat(view.getId()).isEqualTo(DETAIL_ID);
        assertThat(view.getCategory()).isEqualTo("식비");
        assertThat(view.getAmount()).isEqualTo(8_000);
        assertThat(view.getDocumentImageUrls()).extracting(LedgerDocumentInfoView::getDocumentImageUrl)
                .containsExactly("https://img/d1.png", "https://img/d2.png");
        then(ledgerReceiptManager).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("V2 등록 시 카테고리가 빈 문자열이면 카테고리 없이 등록하고 증빙 자료가 없으면 생성하지 않는다.")
    void createLedgerV2_emptyCategoryAndDocuments() {
        // given
        User user = user();
        Ledger ledger = ledger(0);
        LedgerDetail created = detail(ledger, user, FundType.INCOME, 10_000, null);
        CreateLedgerRequestV2 request = new CreateLedgerRequestV2(
                "회비", FundType.INCOME, 10_000, "회비", PAYMENT_DATE, List.of(), "");

        givenStaffContext(user, ledger, AgencyUserRole.STAFF);
        given(ledgerDetailService.createLedgerDetail(
                ledger, user, "회비", FundType.INCOME, 10_000, 0, "회비", PAYMENT_DATE, null))
                .willReturn(created);

        // when
        LedgerDetailInfoViewV2 view = ledgerService.createLedgerV2(USER_ID, LEDGER_ID, request);

        // then
        assertThat(view.getCategory()).isNull();
        assertThat(view.getDocumentImageUrls()).isEmpty();
        then(categoryRepository).shouldHaveNoInteractions();
        then(ledgerDocumentManager).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("V2 등록 시 카테고리가 null이면 카테고리를 조회하지 않는다.")
    void createLedgerV2_nullCategory() {
        // given
        User user = user();
        Ledger ledger = ledger(0);
        LedgerDetail created = detail(ledger, user, FundType.INCOME, 10_000, null);
        CreateLedgerRequestV2 request = new CreateLedgerRequestV2(
                "회비", FundType.INCOME, 10_000, "회비", PAYMENT_DATE, List.of(), null);

        givenStaffContext(user, ledger, AgencyUserRole.STAFF);
        given(ledgerDetailService.createLedgerDetail(
                ledger, user, "회비", FundType.INCOME, 10_000, 0, "회비", PAYMENT_DATE, null))
                .willReturn(created);

        // when
        LedgerDetailInfoViewV2 view = ledgerService.createLedgerV2(USER_ID, LEDGER_ID, request);

        // then
        assertThat(view.getCategory()).isNull();
        then(categoryRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("V2 등록 시 소속에 없는 카테고리를 지정하면 LEDGER_CATEGORY_NOT_FOUND 예외가 발생하고 내역이 등록되지 않는다.")
    void createLedgerV2_categoryNotFound() {
        // given
        User user = user();
        Ledger ledger = ledger(0);
        CreateLedgerRequestV2 request = new CreateLedgerRequestV2(
                "회비", FundType.INCOME, 10_000, "회비", PAYMENT_DATE, List.of(), "없는카테고리");

        givenStaffContext(user, ledger, AgencyUserRole.STAFF);
        given(categoryRepository.findByAgencyIdAndName(AGENCY_ID, "없는카테고리")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerService.createLedgerV2(USER_ID, LEDGER_ID, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_CATEGORY_NOT_FOUND);

        then(ledgerDetailService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("V2 등록 시 차단된 회원이면 INVALID_AGENCY_USER_ACCESS 예외가 발생한다.")
    void createLedgerV2_blocked() {
        // given
        User user = user();
        Ledger ledger = ledger(0);
        givenStaffContext(user, ledger, AgencyUserRole.BLOCKED);
        CreateLedgerRequestV2 request = new CreateLedgerRequestV2(
                "회비", FundType.INCOME, 10_000, "회비", PAYMENT_DATE, List.of(), "식비");

        // when & then
        assertThatThrownBy(() -> ledgerService.createLedgerV2(USER_ID, LEDGER_ID, request))
                .isInstanceOf(InvalidAccessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);

        then(categoryRepository).shouldHaveNoInteractions();
        then(ledgerDetailService).shouldHaveNoInteractions();
    }

    // ===== updateLedger =====

    @Test
    @DisplayName("장부 내역 수정 요청은 조회한 유저와 내역으로 LedgerDetailService에 위임된다.")
    void updateLedger_delegates() {
        // given
        User user = user();
        LedgerDetail target = detail(ledger(0), user, FundType.INCOME, 1_000, null);
        UpdateLedgerRequest request = new UpdateLedgerRequest();
        LedgerDetailInfoView expected = LedgerDetailInfoView.builder().id(DETAIL_ID).build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(target));
        given(ledgerDetailService.updateLedgerDetail(user, target, request)).willReturn(expected);

        // when
        LedgerDetailInfoView result = ledgerService.updateLedger(USER_ID, DETAIL_ID, request);

        // then
        assertThat(result).isSameAs(expected);
        then(ledgerDetailService).should().updateLedgerDetail(user, target, request);
    }

    @Test
    @DisplayName("존재하지 않는 장부 내역을 수정하면 LEDGER_DETAIL_NOT_FOUND 예외가 발생한다.")
    void updateLedger_detailNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerService.updateLedger(USER_ID, DETAIL_ID, new UpdateLedgerRequest()))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_DETAIL_NOT_FOUND);

        then(ledgerDetailService).shouldHaveNoInteractions();
    }

    // ===== deleteLedger =====

    @Test
    @DisplayName("소속의 장부를 삭제하면 모든 장부 내역을 먼저 삭제한 뒤 장부를 삭제한다.")
    void deleteLedger_success() {
        // given
        Ledger ledger = ledger(0);
        List<LedgerDetail> details = List.of(
                detail(ledger, user(), FundType.INCOME, 1_000, null),
                detail(ledger, user(), FundType.EXPENSE, 500, null));
        given(ledgerRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.of(ledger));
        given(ledgerDetailRepository.findAllByLedger(ledger)).willReturn(details);

        // when
        ledgerService.deleteLedger(AGENCY_ID);

        // then
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(ledgerDetailRepository, ledgerRepository);
        inOrder.verify(ledgerDetailRepository).deleteAll(details);
        inOrder.verify(ledgerRepository).delete(ledger);
    }

    @Test
    @DisplayName("장부가 없는 소속의 장부를 삭제하면 LEDGER_NOT_FOUND 예외가 발생한다.")
    void deleteLedger_notFound() {
        // given
        given(ledgerRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerService.deleteLedger(AGENCY_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_NOT_FOUND);

        then(ledgerDetailRepository).shouldHaveNoInteractions();
        then(ledgerRepository).should(never()).delete(any());
    }

    // ===== fixtures =====

    private void givenStaffContext(User user, Ledger ledger, AgencyUserRole role) {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(ledgerRepository.findById(LEDGER_ID)).willReturn(Optional.of(ledger));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(AgencyUser.builder()
                        .id(50L)
                        .agency(ledger.getAgency())
                        .userId(USER_ID)
                        .agencyUserRole(role)
                        .build()));
    }

    private Ledger ledger(int totalBalance) {
        Agency agency = Agency.builder().id(AGENCY_ID).agencyName("머니몽 동아리").build();
        return Ledger.builder().id(LEDGER_ID).agency(agency).totalBalance(totalBalance).build();
    }

    private User user() {
        return User.builder().id(USER_ID).nickname("머니몽").provider("KAKAO").oauthId("oauth").build();
    }

    private LedgerDetail detail(Ledger ledger, User user, FundType fundType, int amount, Category category) {
        return LedgerDetail.builder()
                .id(DETAIL_ID)
                .ledger(ledger)
                .user(user)
                .storeInfo("가게")
                .fundType(fundType)
                .amount(amount)
                .balance(0)
                .description("설명")
                .paymentDate(PAYMENT_DATE)
                .category(category)
                .build();
    }
}
