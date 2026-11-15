package com.moneymong.domain.ledger.service.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.ledger.api.response.LedgerDetailInfoView;
import com.moneymong.domain.ledger.api.response.LedgerDocumentInfoView;
import com.moneymong.domain.ledger.api.response.LedgerReceiptInfoView;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.entity.LedgerDocument;
import com.moneymong.domain.ledger.entity.LedgerReceipt;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.domain.user.entity.User;
import com.moneymong.domain.user.repository.UserRepository;
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
class LedgerDetailReaderTest {

    private static final Long USER_ID = 1L;
    private static final Long LEDGER_DETAIL_ID = 50L;
    private static final ZonedDateTime PAYMENT_DATE =
            ZonedDateTime.of(2024, 3, 15, 12, 30, 0, 0, ZoneId.systemDefault());

    @Mock
    private LedgerReceiptReader ledgerReceiptReader;

    @Mock
    private LedgerDocumentReader ledgerDocumentReader;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerDetailRepository ledgerDetailRepository;

    @InjectMocks
    private LedgerDetailReader ledgerDetailReader;

    @Test
    @DisplayName("장부 상세 조회 시 영수증, 증빙 자료, 작성자, 카테고리를 함께 반환한다.")
    void getLedgerDetail_success() {
        // given
        User author = user(2L, "작성자");
        LedgerDetail ledgerDetail = ledgerDetail(author, Category.of("식비", 10L));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "조회자")));
        given(ledgerDetailRepository.findById(LEDGER_DETAIL_ID)).willReturn(Optional.of(ledgerDetail));
        given(ledgerReceiptReader.getLedgerReceipts(LEDGER_DETAIL_ID)).willReturn(List.of(
                LedgerReceipt.builder().id(1L).ledgerDetail(ledgerDetail).receiptImageUrl("https://img/r1.png").build(),
                LedgerReceipt.builder().id(2L).ledgerDetail(ledgerDetail).receiptImageUrl("https://img/r2.png").build()
        ));
        given(ledgerDocumentReader.getLedgerDocuments(LEDGER_DETAIL_ID)).willReturn(List.of(
                LedgerDocument.builder().id(3L).ledgerDetail(ledgerDetail).documentImageUrl("https://img/d1.png").build()
        ));

        // when
        LedgerDetailInfoView result = ledgerDetailReader.getLedgerDetail(USER_ID, LEDGER_DETAIL_ID);

        // then
        assertThat(result.getId()).isEqualTo(LEDGER_DETAIL_ID);
        assertThat(result.getStoreInfo()).isEqualTo("편의점");
        assertThat(result.getAmount()).isEqualTo(4_500);
        assertThat(result.getFundType()).isEqualTo(FundType.EXPENSE);
        assertThat(result.getDescription()).isEqualTo("간식 구매");
        assertThat(result.getPaymentDate()).isEqualTo(PAYMENT_DATE);
        assertThat(result.getAuthorName()).isEqualTo("작성자");
        assertThat(result.getCategory()).isEqualTo("식비");
        assertThat(result.getReceiptImageUrls())
                .extracting(LedgerReceiptInfoView::getId, LedgerReceiptInfoView::getReceiptImageUrl)
                .containsExactly(tuple(1L, "https://img/r1.png"), tuple(2L, "https://img/r2.png"));
        assertThat(result.getDocumentImageUrls())
                .extracting(LedgerDocumentInfoView::getId, LedgerDocumentInfoView::getDocumentImageUrl)
                .containsExactly(tuple(3L, "https://img/d1.png"));
    }

    @Test
    @DisplayName("카테고리와 첨부 자료가 없는 장부 상세는 카테고리 null과 빈 목록으로 반환한다.")
    void getLedgerDetail_withoutCategoryAndAttachments() {
        // given
        LedgerDetail ledgerDetail = ledgerDetail(user(2L, "작성자"), null);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "조회자")));
        given(ledgerDetailRepository.findById(LEDGER_DETAIL_ID)).willReturn(Optional.of(ledgerDetail));
        given(ledgerReceiptReader.getLedgerReceipts(LEDGER_DETAIL_ID)).willReturn(Collections.emptyList());
        given(ledgerDocumentReader.getLedgerDocuments(LEDGER_DETAIL_ID)).willReturn(Collections.emptyList());

        // when
        LedgerDetailInfoView result = ledgerDetailReader.getLedgerDetail(USER_ID, LEDGER_DETAIL_ID);

        // then
        assertThat(result.getCategory()).isNull();
        assertThat(result.getReceiptImageUrls()).isEmpty();
        assertThat(result.getDocumentImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("유저가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다.")
    void getLedgerDetail_userNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDetailReader.getLedgerDetail(USER_ID, LEDGER_DETAIL_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        then(ledgerDetailRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("장부 상세 내역이 존재하지 않으면 LEDGER_DETAIL_NOT_FOUND 예외가 발생한다.")
    void getLedgerDetail_ledgerDetailNotFound() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "조회자")));
        given(ledgerDetailRepository.findById(LEDGER_DETAIL_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ledgerDetailReader.getLedgerDetail(USER_ID, LEDGER_DETAIL_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LEDGER_DETAIL_NOT_FOUND);
        then(ledgerReceiptReader).should(never()).getLedgerReceipts(any());
        then(ledgerDocumentReader).should(never()).getLedgerDocuments(any());
    }

    private User user(Long id, String nickname) {
        return User.builder()
                .id(id)
                .nickname(nickname)
                .build();
    }

    private LedgerDetail ledgerDetail(User author, Category category) {
        return LedgerDetail.builder()
                .id(LEDGER_DETAIL_ID)
                .user(author)
                .storeInfo("편의점")
                .fundType(FundType.EXPENSE)
                .amount(4_500)
                .balance(5_500)
                .description("간식 구매")
                .paymentDate(PAYMENT_DATE)
                .category(category)
                .build();
    }
}
