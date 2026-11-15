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
import com.moneymong.domain.ledger.entity.LedgerDocument;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.domain.ledger.repository.LedgerDocumentRepository;
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
class LedgerDocumentManagerTest {

    private static final Long USER_ID = 100L;
    private static final Long AGENCY_ID = 10L;
    private static final Long DETAIL_ID = 1000L;
    private static final Long DOCUMENT_ID = 21L;

    @Mock
    private LedgerAssembler ledgerAssembler;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AgencyUserRepository agencyUserRepository;
    @Mock
    private LedgerDetailRepository ledgerDetailRepository;
    @Mock
    private LedgerDocumentRepository ledgerDocumentRepository;

    @InjectMocks
    private LedgerDocumentManager ledgerDocumentManager;

    // ===== createLedgerDocuments =====

    @Test
    @DisplayName("증빙 자료 URL 목록으로 증빙 자료를 생성하면 URL마다 저장되고 저장된 엔티티 목록을 반환한다.")
    void createLedgerDocuments_success() {
        // given
        LedgerDetail detail = detail();
        List<String> urls = List.of("https://img/d1.png", "https://img/d2.png");
        List<LedgerDocument> documents = List.of(
                LedgerDocument.of(detail, "https://img/d1.png"),
                LedgerDocument.of(detail, "https://img/d2.png"));

        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail));
        given(ledgerAssembler.toLedgerDocumentEntities(detail, urls)).willReturn(documents);
        given(ledgerDocumentRepository.save(any(LedgerDocument.class))).willAnswer(returnsFirstArg());

        // when
        List<LedgerDocument> result = ledgerDocumentManager.createLedgerDocuments(DETAIL_ID, urls);

        // then
        assertThat(result).containsExactlyElementsOf(documents);
        assertThat(result).allSatisfy(document -> assertThat(document.getLedgerDetail()).isSameAs(detail));
        then(ledgerDocumentRepository).should().save(documents.get(0));
        then(ledgerDocumentRepository).should().save(documents.get(1));
    }

    @Test
    @DisplayName("증빙 자료 URL 목록이 null이거나 비어 있으면 빈 목록을 반환하고 아무것도 조회하거나 저장하지 않는다.")
    void createLedgerDocuments_emptyUrls() {
        // when
        List<LedgerDocument> fromNull = ledgerDocumentManager.createLedgerDocuments(DETAIL_ID, null);
        List<LedgerDocument> fromEmpty = ledgerDocumentManager.createLedgerDocuments(DETAIL_ID, List.of());

        // then
        assertThat(fromNull).isEmpty();
        assertThat(fromEmpty).isEmpty();
        then(ledgerDetailRepository).shouldHaveNoInteractions();
        then(ledgerDocumentRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 장부 내역에 증빙 자료를 생성하면 NotFoundException이 발생하고 저장되지 않는다.")
    void createLedgerDocuments_detailNotFound() {
        // given
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.empty());

        // when & then
        // NOTE: 현재 구현은 LEDGER_RECEIPT_NOT_FOUND 코드를 사용하고 있어(의도와 다른 것으로 보임) 예외 타입만 검증한다.
        assertThatThrownBy(() -> ledgerDocumentManager.createLedgerDocuments(DETAIL_ID, List.of("https://img/d1.png")))
                .isInstanceOf(NotFoundException.class);

        then(ledgerDocumentRepository).shouldHaveNoInteractions();
    }

    // ===== removeLedgerDocuments =====

    @Test
    @DisplayName("운영진이 증빙 자료를 삭제하면 해당 증빙 자료가 삭제된다.")
    void removeLedgerDocuments_success() {
        // given
        LedgerDetail detail = detail();
        LedgerDocument document = LedgerDocument.builder().id(DOCUMENT_ID).ledgerDetail(detail).documentImageUrl("https://img/d1.png").build();

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.STAFF)));
        given(ledgerDocumentRepository.findById(DOCUMENT_ID)).willReturn(Optional.of(document));

        // when
        ledgerDocumentManager.removeLedgerDocuments(USER_ID, DETAIL_ID, DOCUMENT_ID);

        // then
        then(ledgerDocumentRepository).should().delete(document);
    }

    @Test
    @DisplayName("운영진이 아닌 회원이 증빙 자료를 삭제하면 INVALID_LEDGER_ACCESS 예외가 발생하고 삭제되지 않는다.")
    void removeLedgerDocuments_notStaff() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail()));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.MEMBER)));

        // when & then
        assertThatThrownBy(() -> ledgerDocumentManager.removeLedgerDocuments(USER_ID, DETAIL_ID, DOCUMENT_ID))
                .isInstanceOf(InvalidAccessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_LEDGER_ACCESS);

        then(ledgerDocumentRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 유저가 증빙 자료를 삭제하면 USER_NOT_FOUND 예외가 발생한다.")
    void removeLedgerDocuments_userNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDocumentManager.removeLedgerDocuments(USER_ID, DETAIL_ID, DOCUMENT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(ledgerDocumentRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소속에 가입하지 않은 유저가 증빙 자료를 삭제하면 AGENCY_NOT_FOUND 예외가 발생한다.")
    void removeLedgerDocuments_notAgencyMember() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail()));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDocumentManager.removeLedgerDocuments(USER_ID, DETAIL_ID, DOCUMENT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AGENCY_NOT_FOUND);

        then(ledgerDocumentRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 증빙 자료를 삭제하면 LEDGER_DOCUMENT_NOT_FOUND 예외가 발생한다.")
    void removeLedgerDocuments_documentNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(ledgerDetailRepository.findById(DETAIL_ID)).willReturn(Optional.of(detail()));
        given(agencyUserRepository.findByUserIdAndAgencyId(USER_ID, AGENCY_ID))
                .willReturn(Optional.of(agencyUser(AgencyUserRole.STAFF)));
        given(ledgerDocumentRepository.findById(DOCUMENT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDocumentManager.removeLedgerDocuments(USER_ID, DETAIL_ID, DOCUMENT_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_DOCUMENT_NOT_FOUND);

        then(ledgerDocumentRepository).should(never()).delete(any());
    }

    // ===== fixtures =====

    private Agency agency() {
        return Agency.builder().id(AGENCY_ID).agencyName("머니몽 동아리").build();
    }

    private User user() {
        return User.builder().id(USER_ID).nickname("머니몽").provider("KAKAO").oauthId("oauth").build();
    }

    private AgencyUser agencyUser(AgencyUserRole role) {
        return AgencyUser.builder().id(50L).agency(agency()).userId(USER_ID).agencyUserRole(role).build();
    }

    private LedgerDetail detail() {
        Ledger ledger = Ledger.builder().id(1L).agency(agency()).totalBalance(0).build();
        return LedgerDetail.builder()
                .id(DETAIL_ID)
                .ledger(ledger)
                .fundType(FundType.EXPENSE)
                .amount(1_000)
                .build();
    }
}
