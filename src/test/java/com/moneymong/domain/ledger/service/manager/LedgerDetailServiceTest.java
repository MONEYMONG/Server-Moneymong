package com.moneymong.domain.ledger.service.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.category.repository.CategoryRepository;
import com.moneymong.domain.ledger.api.request.UpdateLedgerRequest;
import com.moneymong.domain.ledger.api.request.UpdateLedgerRequestV2;
import com.moneymong.domain.ledger.api.response.LedgerDetailInfoView;
import com.moneymong.domain.ledger.api.response.LedgerDocumentInfoView;
import com.moneymong.domain.ledger.api.response.LedgerReceiptInfoView;
import com.moneymong.domain.ledger.entity.Ledger;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.entity.LedgerDocument;
import com.moneymong.domain.ledger.entity.LedgerReceipt;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.domain.ledger.repository.LedgerDocumentRepository;
import com.moneymong.domain.ledger.repository.LedgerReceiptRepository;
import com.moneymong.domain.ledger.service.mapper.LedgerAssembler;
import com.moneymong.domain.ledger.service.reader.LedgerDocumentReader;
import com.moneymong.domain.ledger.service.reader.LedgerReceiptReader;
import com.moneymong.domain.user.entity.User;
import com.moneymong.domain.user.repository.UserRepository;
import com.moneymong.global.exception.custom.BadRequestException;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerDetailServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long AGENCY_ID = 10L;
    private static final Long LEDGER_ID = 1L;
    private static final Long DETAIL_ID = 1000L;
    private static final ZonedDateTime DAY_1 = ZonedDateTime.of(2024, 3, 1, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"));
    private static final ZonedDateTime DAY_2 = DAY_1.plusDays(1);
    private static final ZonedDateTime DAY_3 = DAY_1.plusDays(2);

    @Mock
    private LedgerAssembler ledgerAssembler;
    @Mock
    private LedgerReceiptReader ledgerReceiptReader;
    @Mock
    private LedgerDocumentReader ledgerDocumentReader;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AgencyUserRepository agencyUserRepository;
    @Mock
    private LedgerDetailRepository ledgerDetailRepository;
    @Mock
    private LedgerReceiptRepository ledgerReceiptRepository;
    @Mock
    private LedgerDocumentRepository ledgerDocumentRepository;
    @Mock
    private LedgerReceiptManager ledgerReceiptManager;
    @Mock
    private LedgerDocumentManager ledgerDocumentManager;
    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private LedgerDetailService ledgerDetailService;

    // ===== createLedgerDetail =====

    @Test
    @DisplayName("수입 내역을 등록하면 총 잔액이 증가하고 결제일 순으로 잔액이 재계산된다.")
    void createLedgerDetail_income() {
        // given
        Ledger ledger = ledger(10_000);
        User user = user();
        LedgerDetail existing = detail(1L, ledger, FundType.INCOME, 10_000, DAY_1);
        LedgerDetail created = detail(2L, ledger, FundType.INCOME, 3_000, DAY_2);

        given(ledgerAssembler.toLedgerDetailEntity(ledger, user, "편의점", FundType.INCOME, 3_000, 10_000, "간식", DAY_2, null))
                .willReturn(created);
        given(ledgerDetailRepository.findAllByLedger(ledger)).willReturn(new ArrayList<>(List.of(created, existing)));

        // when
        LedgerDetail result = ledgerDetailService.createLedgerDetail(
                ledger, user, "편의점", FundType.INCOME, 3_000, 10_000, "간식", DAY_2, null);

        // then
        assertThat(result).isSameAs(created);
        assertThat(ledger.getTotalBalance()).isEqualTo(13_000);
        assertThat(existing.getBalance()).isEqualTo(10_000);
        assertThat(created.getBalance()).isEqualTo(13_000);
        then(ledgerDetailRepository).should().save(created);
    }

    @Test
    @DisplayName("지출 내역을 과거 날짜로 등록하면 총 잔액이 감소하고 이후 내역들의 잔액이 다시 계산된다.")
    void createLedgerDetail_expenseInPast() {
        // given
        Ledger ledger = ledger(10_000);
        User user = user();
        LedgerDetail existing = detail(1L, ledger, FundType.INCOME, 10_000, DAY_2);
        LedgerDetail created = detail(2L, ledger, FundType.EXPENSE, 4_000, DAY_1);

        given(ledgerAssembler.toLedgerDetailEntity(ledger, user, "문구점", FundType.EXPENSE, 4_000, 10_000, "비품", DAY_1, null))
                .willReturn(created);
        given(ledgerDetailRepository.findAllByLedger(ledger)).willReturn(new ArrayList<>(List.of(existing, created)));

        // when
        ledgerDetailService.createLedgerDetail(
                ledger, user, "문구점", FundType.EXPENSE, 4_000, 10_000, "비품", DAY_1, null);

        // then
        assertThat(ledger.getTotalBalance()).isEqualTo(6_000);
        assertThat(created.getBalance()).isEqualTo(-4_000);
        assertThat(existing.getBalance()).isEqualTo(6_000);
    }

    @Test
    @DisplayName("등록 후 총 잔액이 최대 한도를 넘으면 LEDGER_AMOUNT_OVERFLOW 예외가 발생하고 저장되지 않는다.")
    void createLedgerDetail_overflow() {
        // given
        Ledger ledger = ledger(999_999_000);
        User user = user();
        LedgerDetail created = detail(2L, ledger, FundType.INCOME, 1_000, DAY_1);
        given(ledgerAssembler.toLedgerDetailEntity(ledger, user, "후원", FundType.INCOME, 1_000, 999_999_000, "후원금", DAY_1, null))
                .willReturn(created);

        // when & then
        assertThatThrownBy(() -> ledgerDetailService.createLedgerDetail(
                ledger, user, "후원", FundType.INCOME, 1_000, 999_999_000, "후원금", DAY_1, null))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_AMOUNT_OVERFLOW);

        assertThat(ledger.getTotalBalance()).isEqualTo(999_999_000);
        then(ledgerDetailRepository).should(never()).save(any());
    }

    // ===== updateLedgerDetail (V1) =====

    @Test
    @DisplayName("수입 내역의 금액을 줄이면 차액만큼 총 잔액이 감소하고 수정된 내역 정보를 반환한다.")
    void updateLedgerDetail_incomeDecrease() {
        // given
        Ledger ledger = ledger(5_000);
        User user = user();
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.INCOME, 5_000, DAY_1);
        UpdateLedgerRequest request = updateRequest("카페", 3_000, "회의 음료", DAY_2);

        LedgerReceipt receipt = receipt(11L, target, "https://img/receipt.png");
        LedgerDocument document = document(21L, target, "https://img/document.png");

        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.STAFF)));
        given(ledgerDetailRepository.findAllByLedger(ledger)).willReturn(new ArrayList<>(List.of(target)));
        given(ledgerReceiptReader.getLedgerReceipts(DETAIL_ID)).willReturn(List.of(receipt));
        given(ledgerDocumentReader.getLedgerDocuments(DETAIL_ID)).willReturn(List.of(document));

        // when
        LedgerDetailInfoView view = ledgerDetailService.updateLedgerDetail(user, target, request);

        // then
        assertThat(ledger.getTotalBalance()).isEqualTo(3_000);
        assertThat(target.getAmount()).isEqualTo(3_000);
        assertThat(target.getBalance()).isEqualTo(3_000);
        assertThat(target.getStoreInfo()).isEqualTo("카페");
        assertThat(target.getPaymentDate()).isEqualTo(DAY_2);

        assertThat(view.getId()).isEqualTo(DETAIL_ID);
        assertThat(view.getAmount()).isEqualTo(3_000);
        assertThat(view.getDescription()).isEqualTo("회의 음료");
        assertThat(view.getAuthorName()).isEqualTo("머니몽");
        assertThat(view.getReceiptImageUrls()).extracting(LedgerReceiptInfoView::getReceiptImageUrl)
                .containsExactly("https://img/receipt.png");
        assertThat(view.getDocumentImageUrls()).extracting(LedgerDocumentInfoView::getDocumentImageUrl)
                .containsExactly("https://img/document.png");
    }

    @Test
    @DisplayName("지출 내역의 금액을 늘리면 차액만큼 총 잔액이 감소한다.")
    void updateLedgerDetail_expenseIncrease() {
        // given
        Ledger ledger = ledger(8_000);
        User user = user();
        LedgerDetail income = detail(1L, ledger, FundType.INCOME, 10_000, DAY_1);
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.EXPENSE, 2_000, DAY_2);
        UpdateLedgerRequest request = updateRequest("식당", 5_000, "회식", DAY_2);

        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.STAFF)));
        given(ledgerDetailRepository.findAllByLedger(ledger)).willReturn(new ArrayList<>(List.of(target, income)));
        given(ledgerReceiptReader.getLedgerReceipts(DETAIL_ID)).willReturn(List.of());
        given(ledgerDocumentReader.getLedgerDocuments(DETAIL_ID)).willReturn(List.of());

        // when
        ledgerDetailService.updateLedgerDetail(user, target, request);

        // then
        assertThat(ledger.getTotalBalance()).isEqualTo(5_000);
        assertThat(income.getBalance()).isEqualTo(10_000);
        assertThat(target.getBalance()).isEqualTo(5_000);
    }

    @Test
    @DisplayName("운영진이 아닌 회원이 장부 내역을 수정하면 INVALID_AGENCY_USER_ACCESS 예외가 발생한다.")
    void updateLedgerDetail_notStaff() {
        // given
        Ledger ledger = ledger(5_000);
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.INCOME, 5_000, DAY_1);
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.MEMBER)));

        // when & then
        assertThatThrownBy(() -> ledgerDetailService.updateLedgerDetail(
                user(), target, updateRequest("카페", 3_000, "음료", DAY_1)))
                .isInstanceOf(InvalidAccessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);

        then(ledgerDetailRepository).should(never()).findAllByLedger(any());
        then(ledgerReceiptReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소속에 가입하지 않은 유저가 장부 내역을 수정하면 AGENCY_NOT_FOUND 예외가 발생한다.")
    void updateLedgerDetail_notAgencyMember() {
        // given
        Ledger ledger = ledger(5_000);
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.INCOME, 5_000, DAY_1);
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDetailService.updateLedgerDetail(
                user(), target, updateRequest("카페", 3_000, "음료", DAY_1)))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);
    }

    // ===== updateLedgerDetailV2 =====

    @Test
    @DisplayName("V2 수정 시 카테고리와 금액이 반영되고 기존 영수증과 증빙 자료가 새 목록으로 교체된다.")
    void updateLedgerDetailV2_success() {
        // given
        Ledger ledger = ledger(10_000);
        User user = user();
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.EXPENSE, 2_000, DAY_1);
        Category category = Category.builder().id(5L).name("식비").agencyId(AGENCY_ID).build();
        List<String> receiptUrls = List.of("https://img/r1.png");
        List<String> documentUrls = List.of("https://img/d1.png", "https://img/d2.png");
        UpdateLedgerRequestV2 request = updateRequestV2("식당", 1_500, "점심", DAY_2, receiptUrls, documentUrls, "식비");

        List<LedgerReceipt> receipts = List.of(receipt(11L, target, "https://img/r1.png"));
        List<LedgerDocument> documents = List.of(
                document(21L, target, "https://img/d1.png"),
                document(22L, target, "https://img/d2.png"));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(target));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.STAFF)));
        given(categoryRepository.findByAgencyIdAndName(AGENCY_ID, "식비")).willReturn(Optional.of(category));
        given(ledgerDetailRepository.findAllByLedger(ledger)).willReturn(new ArrayList<>(List.of(target)));
        given(ledgerReceiptManager.createReceipts(DETAIL_ID, receiptUrls)).willReturn(receipts);
        given(ledgerDocumentManager.createLedgerDocuments(DETAIL_ID, documentUrls)).willReturn(documents);

        // when
        LedgerDetailInfoView view = ledgerDetailService.updateLedgerDetailV2(USER_ID, DETAIL_ID, request);

        // then
        // 지출 2,000 -> 1,500 : 총 잔액 500 증가
        assertThat(ledger.getTotalBalance()).isEqualTo(10_500);
        assertThat(target.getAmount()).isEqualTo(1_500);
        assertThat(target.getBalance()).isEqualTo(-1_500);
        assertThat(target.getCategory()).isSameAs(category);

        InOrder inOrder = inOrder(ledgerReceiptRepository, ledgerDocumentRepository, ledgerReceiptManager, ledgerDocumentManager);
        inOrder.verify(ledgerReceiptRepository).deleteByLedgerDetail(target);
        inOrder.verify(ledgerDocumentRepository).deleteByLedgerDetail(target);
        inOrder.verify(ledgerReceiptManager).createReceipts(DETAIL_ID, receiptUrls);
        inOrder.verify(ledgerDocumentManager).createLedgerDocuments(DETAIL_ID, documentUrls);
        then(ledgerReceiptRepository).should().saveAll(receipts);
        then(ledgerDocumentRepository).should().saveAll(documents);

        assertThat(view.getCategory()).isEqualTo("식비");
        assertThat(view.getAmount()).isEqualTo(1_500);
        assertThat(view.getReceiptImageUrls()).hasSize(1);
        assertThat(view.getDocumentImageUrls()).extracting(LedgerDocumentInfoView::getDocumentImageUrl)
                .containsExactly("https://img/d1.png", "https://img/d2.png");
    }

    @Test
    @DisplayName("V2 수정 시 카테고리가 비어 있으면 카테고리를 조회하지 않고 null로 저장한다.")
    void updateLedgerDetailV2_emptyCategory() {
        // given
        Ledger ledger = ledger(3_000);
        User user = user();
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.INCOME, 3_000, DAY_1);
        ReflectionTestUtils.setField(target, "category", Category.builder().id(5L).name("식비").agencyId(AGENCY_ID).build());
        UpdateLedgerRequestV2 request = updateRequestV2("후원", 4_000, "후원금", DAY_1, List.of(), List.of(), "");

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(target));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.STAFF)));
        given(ledgerDetailRepository.findAllByLedger(ledger)).willReturn(new ArrayList<>(List.of(target)));
        given(ledgerReceiptManager.createReceipts(DETAIL_ID, List.of())).willReturn(List.of());
        given(ledgerDocumentManager.createLedgerDocuments(DETAIL_ID, List.of())).willReturn(List.of());

        // when
        LedgerDetailInfoView view = ledgerDetailService.updateLedgerDetailV2(USER_ID, DETAIL_ID, request);

        // then
        assertThat(ledger.getTotalBalance()).isEqualTo(4_000);
        assertThat(target.getCategory()).isNull();
        assertThat(view.getCategory()).isNull();
        then(categoryRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("V2 수정 시 존재하지 않는 카테고리를 지정하면 LEDGER_CATEGORY_NOT_FOUND 예외가 발생한다.")
    void updateLedgerDetailV2_categoryNotFound() {
        // given
        Ledger ledger = ledger(3_000);
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.INCOME, 3_000, DAY_1);
        UpdateLedgerRequestV2 request = updateRequestV2("후원", 3_000, "후원금", DAY_1, List.of(), List.of(), "없는카테고리");

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(target));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.STAFF)));
        given(categoryRepository.findByAgencyIdAndName(AGENCY_ID, "없는카테고리")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDetailService.updateLedgerDetailV2(USER_ID, DETAIL_ID, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_CATEGORY_NOT_FOUND);

        then(ledgerReceiptRepository).should(never()).deleteByLedgerDetail(any());
    }

    @Test
    @DisplayName("V2 수정 시 운영진이 아니면 INVALID_AGENCY_USER_ACCESS 예외가 발생하고 잔액과 첨부가 변경되지 않는다.")
    void updateLedgerDetailV2_notStaff() {
        // given
        Ledger ledger = ledger(3_000);
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.INCOME, 3_000, DAY_1);
        UpdateLedgerRequestV2 request = updateRequestV2("후원", 1_000, "후원금", DAY_1, List.of(), List.of(), null);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(target));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.BLOCKED)));

        // when & then
        assertThatThrownBy(() -> ledgerDetailService.updateLedgerDetailV2(USER_ID, DETAIL_ID, request))
                .isInstanceOf(InvalidAccessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);

        assertThat(ledger.getTotalBalance()).isEqualTo(3_000);
        assertThat(target.getAmount()).isEqualTo(3_000);
        then(ledgerReceiptRepository).shouldHaveNoInteractions();
        then(ledgerDocumentRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("V2 수정 시 존재하지 않는 유저이면 USER_NOT_FOUND 예외가 발생한다.")
    void updateLedgerDetailV2_userNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDetailService.updateLedgerDetailV2(USER_ID, DETAIL_ID,
                updateRequestV2("후원", 1_000, "후원금", DAY_1, List.of(), List.of(), null)))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(ledgerDetailRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("V2 수정 시 존재하지 않는 장부 내역이면 LEDGER_DETAIL_NOT_FOUND 예외가 발생한다.")
    void updateLedgerDetailV2_detailNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDetailService.updateLedgerDetailV2(USER_ID, DETAIL_ID,
                updateRequestV2("후원", 1_000, "후원금", DAY_1, List.of(), List.of(), null)))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_DETAIL_NOT_FOUND);

        then(agencyUserRepository).shouldHaveNoInteractions();
    }

    // ===== removeLedgerDetail =====

    @Test
    @DisplayName("수입 내역을 삭제하면 첨부와 내역이 삭제되고 총 잔액이 감소하며 남은 내역 잔액이 재계산된다.")
    void removeLedgerDetail_income() {
        // given
        Ledger ledger = ledger(10_000);
        LedgerDetail first = detail(1L, ledger, FundType.INCOME, 3_000, DAY_1);
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.INCOME, 9_000, DAY_2);
        LedgerDetail last = detail(3L, ledger, FundType.EXPENSE, 2_000, DAY_3);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(target));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.STAFF)));
        given(ledgerDetailRepository.findAllByLedger(ledger)).willReturn(new ArrayList<>(List.of(last, first)));

        // when
        ledgerDetailService.removeLedgerDetail(USER_ID, DETAIL_ID);

        // then
        InOrder inOrder = inOrder(ledgerReceiptRepository, ledgerDocumentRepository, ledgerDetailRepository);
        inOrder.verify(ledgerReceiptRepository).deleteByLedgerDetail(target);
        inOrder.verify(ledgerDocumentRepository).deleteByLedgerDetail(target);
        inOrder.verify(ledgerDetailRepository).delete(target);

        assertThat(ledger.getTotalBalance()).isEqualTo(1_000);
        assertThat(first.getBalance()).isEqualTo(3_000);
        assertThat(last.getBalance()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("지출 내역을 삭제하면 총 잔액이 지출 금액만큼 증가한다.")
    void removeLedgerDetail_expense() {
        // given
        Ledger ledger = ledger(7_000);
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.EXPENSE, 3_000, DAY_1);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(target));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.STAFF)));
        given(ledgerDetailRepository.findAllByLedger(ledger)).willReturn(new ArrayList<>());

        // when
        ledgerDetailService.removeLedgerDetail(USER_ID, DETAIL_ID);

        // then
        assertThat(ledger.getTotalBalance()).isEqualTo(10_000);
        then(ledgerDetailRepository).should().delete(target);
    }

    @Test
    @DisplayName("운영진이 아닌 회원이 장부 내역을 삭제하면 INVALID_AGENCY_USER_ACCESS 예외가 발생하고 삭제되지 않는다.")
    void removeLedgerDetail_notStaff() {
        // given
        Ledger ledger = ledger(7_000);
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.EXPENSE, 3_000, DAY_1);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(target));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.MEMBER)));

        // when & then
        assertThatThrownBy(() -> ledgerDetailService.removeLedgerDetail(USER_ID, DETAIL_ID))
                .isInstanceOf(InvalidAccessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AGENCY_USER_ACCESS);

        assertThat(ledger.getTotalBalance()).isEqualTo(7_000);
        then(ledgerDetailRepository).should(never()).delete(any());
        then(ledgerReceiptRepository).shouldHaveNoInteractions();
        then(ledgerDocumentRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 장부 내역을 삭제하면 LEDGER_DETAIL_NOT_FOUND 예외가 발생한다.")
    void removeLedgerDetail_detailNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDetailService.removeLedgerDetail(USER_ID, DETAIL_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_DETAIL_NOT_FOUND);

        then(agencyUserRepository).should(never()).findByUserIdAndAgencyId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("소속에 가입하지 않은 유저가 장부 내역을 삭제하면 AGENCY_NOT_FOUND 예외가 발생한다.")
    void removeLedgerDetail_notAgencyMember() {
        // given
        Ledger ledger = ledger(7_000);
        LedgerDetail target = detail(DETAIL_ID, ledger, FundType.EXPENSE, 3_000, DAY_1);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(target));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDetailService.removeLedgerDetail(USER_ID, DETAIL_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);

        then(ledgerDetailRepository).should(never()).delete(any());
        then(categoryRepository).should(never()).findByAgencyIdAndName(anyLong(), anyString());
    }

    // ===== fixtures =====

    private Agency agency() {
        return Agency.builder().id(AGENCY_ID).agencyName("머니몽 동아리").build();
    }

    private Ledger ledger(int totalBalance) {
        return Ledger.builder().id(LEDGER_ID).agency(agency()).totalBalance(totalBalance).build();
    }

    private User user() {
        return User.builder().id(USER_ID).nickname("머니몽").provider("KAKAO").oauthId("oauth").build();
    }

    private AgencyUser agencyUser(AgencyUserRole role) {
        return AgencyUser.builder().id(50L).agency(agency()).userId(USER_ID).agencyUserRole(role).build();
    }

    private LedgerDetail detail(Long id, Ledger ledger, FundType fundType, int amount, ZonedDateTime paymentDate) {
        return LedgerDetail.builder()
                .id(id)
                .ledger(ledger)
                .user(user())
                .storeInfo("가게")
                .fundType(fundType)
                .amount(amount)
                .balance(0)
                .description("설명")
                .paymentDate(paymentDate)
                .build();
    }

    private LedgerReceipt receipt(Long id, LedgerDetail detail, String url) {
        return LedgerReceipt.builder().id(id).ledgerDetail(detail).receiptImageUrl(url).build();
    }

    private LedgerDocument document(Long id, LedgerDetail detail, String url) {
        return LedgerDocument.builder().id(id).ledgerDetail(detail).documentImageUrl(url).build();
    }

    private UpdateLedgerRequest updateRequest(String storeInfo, int amount, String description, ZonedDateTime paymentDate) {
        UpdateLedgerRequest request = new UpdateLedgerRequest();
        ReflectionTestUtils.setField(request, "storeInfo", storeInfo);
        ReflectionTestUtils.setField(request, "amount", amount);
        ReflectionTestUtils.setField(request, "description", description);
        ReflectionTestUtils.setField(request, "paymentDate", paymentDate);
        return request;
    }

    private UpdateLedgerRequestV2 updateRequestV2(
            String storeInfo,
            int amount,
            String description,
            ZonedDateTime paymentDate,
            List<String> receiptImageUrls,
            List<String> documentImageUrls,
            String category
    ) {
        UpdateLedgerRequestV2 request = new UpdateLedgerRequestV2();
        ReflectionTestUtils.setField(request, "storeInfo", storeInfo);
        ReflectionTestUtils.setField(request, "amount", amount);
        ReflectionTestUtils.setField(request, "description", description);
        ReflectionTestUtils.setField(request, "paymentDate", paymentDate);
        ReflectionTestUtils.setField(request, "receiptImageUrls", receiptImageUrls);
        ReflectionTestUtils.setField(request, "documentImageUrls", documentImageUrls);
        ReflectionTestUtils.setField(request, "category", category);
        return request;
    }
}
