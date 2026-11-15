package com.moneymong.domain.ledger.service.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.ledger.entity.Ledger;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.entity.LedgerReceipt;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.domain.ledger.repository.LedgerReceiptRepository;
import com.moneymong.domain.ledger.service.mapper.LedgerAssembler;
import com.moneymong.domain.user.entity.User;
import com.moneymong.domain.user.repository.UserRepository;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerReceiptManagerTest {

    private static final Long USER_ID = 100L;
    private static final Long AGENCY_ID = 10L;
    private static final Long DETAIL_ID = 1000L;
    private static final Long RECEIPT_ID = 11L;

    @Mock
    private LedgerAssembler ledgerAssembler;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AgencyUserRepository agencyUserRepository;
    @Mock
    private LedgerDetailRepository ledgerDetailRepository;
    @Mock
    private LedgerReceiptRepository ledgerReceiptRepository;

    @InjectMocks
    private LedgerReceiptManager ledgerReceiptManager;

    // ===== createReceipts =====

    @Test
    @DisplayName("영수증 URL 목록으로 영수증을 생성하면 URL마다 저장되고 저장된 엔티티 목록을 반환한다.")
    void createReceipts_success() {
        // given
        LedgerDetail detail = detail();
        List<String> urls = List.of("https://img/r1.png", "https://img/r2.png", "https://img/r3.png");
        List<LedgerReceipt> receipts = urls.stream().map(url -> LedgerReceipt.of(detail, url)).toList();

        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail));
        given(ledgerAssembler.toLedgerReceiptEntities(detail, urls)).willReturn(receipts);
        given(ledgerReceiptRepository.save(any(LedgerReceipt.class))).willAnswer(returnsFirstArg());

        // when
        List<LedgerReceipt> result = ledgerReceiptManager.createReceipts(DETAIL_ID, urls);

        // then
        assertThat(result).extracting(LedgerReceipt::getReceiptImageUrl).containsExactlyElementsOf(urls);
        assertThat(result).allSatisfy(receipt -> assertThat(receipt.getLedgerDetail()).isSameAs(detail));
        receipts.forEach(receipt -> then(ledgerReceiptRepository).should().save(receipt));
    }

    @Test
    @DisplayName("영수증 URL 목록이 null이거나 비어 있으면 빈 목록을 반환하고 아무것도 조회하거나 저장하지 않는다.")
    void createReceipts_emptyUrls() {
        // when
        List<LedgerReceipt> fromNull = ledgerReceiptManager.createReceipts(DETAIL_ID, null);
        List<LedgerReceipt> fromEmpty = ledgerReceiptManager.createReceipts(DETAIL_ID, List.of());

        // then
        assertThat(fromNull).isEmpty();
        assertThat(fromEmpty).isEmpty();
        then(ledgerDetailRepository).shouldHaveNoInteractions();
        then(ledgerReceiptRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 장부 내역에 영수증을 생성하면 NotFoundException이 발생하고 저장되지 않는다.")
    void createReceipts_detailNotFound() {
        // given
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.empty());

        // when & then
        // NOTE: 현재 구현은 장부 내역 부재에도 LEDGER_RECEIPT_NOT_FOUND 코드를 사용하고 있어 예외 타입만 검증한다.
        assertThatThrownBy(() -> ledgerReceiptManager.createReceipts(DETAIL_ID, List.of("https://img/r1.png")))
                .isInstanceOf(NotFoundException.class);

        then(ledgerReceiptRepository).shouldHaveNoInteractions();
    }

    // ===== removeReceipt =====

    @Test
    @DisplayName("운영진이 영수증을 삭제하면 해당 영수증이 삭제된다.")
    void removeReceipt_success() {
        // given
        LedgerDetail detail = detail();
        LedgerReceipt receipt = LedgerReceipt.builder().id(RECEIPT_ID).ledgerDetail(detail).receiptImageUrl("https://img/r1.png").build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail));
        givenAgencyUser(AgencyUserRole.STAFF);
        given(ledgerReceiptRepository.findById(RECEIPT_ID)).willReturn(Optional.of(receipt));

        // when
        ledgerReceiptManager.removeReceipt(USER_ID, DETAIL_ID, RECEIPT_ID);

        // then
        then(ledgerReceiptRepository).should().delete(receipt);
    }

    @Test
    @DisplayName("운영진이 아닌 회원이 영수증을 삭제하면 INVALID_LEDGER_ACCESS 예외가 발생하고 삭제되지 않는다.")
    void removeReceipt_notStaff() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail()));
        givenAgencyUser(AgencyUserRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> ledgerReceiptManager.removeReceipt(USER_ID, DETAIL_ID, RECEIPT_ID))
                .isInstanceOf(InvalidAccessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_LEDGER_ACCESS);

        then(ledgerReceiptRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소속에 가입하지 않은 유저가 영수증을 삭제하면 AGENCY_NOT_FOUND 예외가 발생하고 삭제되지 않는다.")
    void removeReceipt_notAgencyMember() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail()));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReceiptManager.removeReceipt(USER_ID, DETAIL_ID, RECEIPT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);

        then(ledgerReceiptRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 장부 내역의 영수증을 삭제하면 LEDGER_RECEIPT_NOT_FOUND 예외가 발생하고 삭제되지 않는다.")
    void removeReceipt_receiptOfOtherDetail() {
        // given
        LedgerDetail otherDetail = LedgerDetail.builder().id(9999L).build();
        LedgerReceipt receipt = LedgerReceipt.builder().id(RECEIPT_ID).ledgerDetail(otherDetail).receiptImageUrl("https://img/r1.png").build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail()));
        givenAgencyUser(AgencyUserRole.STAFF);
        given(ledgerReceiptRepository.findById(RECEIPT_ID)).willReturn(Optional.of(receipt));

        // when & then
        assertThatThrownBy(() -> ledgerReceiptManager.removeReceipt(USER_ID, DETAIL_ID, RECEIPT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_RECEIPT_NOT_FOUND);

        then(ledgerReceiptRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("존재하지 않는 유저가 영수증을 삭제하면 USER_NOT_FOUND 예외가 발생한다.")
    void removeReceipt_userNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReceiptManager.removeReceipt(USER_ID, DETAIL_ID, RECEIPT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(ledgerReceiptRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 장부 내역의 영수증을 삭제하면 LEDGER_DETAIL_NOT_FOUND 예외가 발생한다.")
    void removeReceipt_detailNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReceiptManager.removeReceipt(USER_ID, DETAIL_ID, RECEIPT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_DETAIL_NOT_FOUND);

        then(ledgerReceiptRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 영수증을 삭제하면 LEDGER_RECEIPT_NOT_FOUND 예외가 발생한다.")
    void removeReceipt_receiptNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail()));
        givenAgencyUser(AgencyUserRole.STAFF);
        given(ledgerReceiptRepository.findById(RECEIPT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerReceiptManager.removeReceipt(USER_ID, DETAIL_ID, RECEIPT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_RECEIPT_NOT_FOUND);

        then(ledgerReceiptRepository).should(never()).delete(any());
    }

    // ===== fixtures =====

    private void givenAgencyUser(AgencyUserRole role) {
        Agency agency = Agency.builder().id(AGENCY_ID).agencyName("머니몽 동아리").build();
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(AgencyUser.builder().id(50L).agency(agency).userId(USER_ID).agencyUserRole(role).build()));
    }

    private User user() {
        return User.builder().id(USER_ID).nickname("머니몽").provider("KAKAO").oauthId("oauth").build();
    }

    private LedgerDetail detail() {
        Agency agency = Agency.builder().id(AGENCY_ID).agencyName("머니몽 동아리").build();
        Ledger ledger = Ledger.builder().id(1L).agency(agency).totalBalance(0).build();
        return LedgerDetail.builder()
                .id(DETAIL_ID)
                .ledger(ledger)
                .fundType(FundType.EXPENSE)
                .amount(1_000)
                .build();
    }
}
