package com.moneymong.domain.ledger.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.ledger.entity.Ledger;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.entity.LedgerDocument;
import com.moneymong.domain.ledger.entity.LedgerReceipt;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.user.entity.User;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LedgerAssemblerTest {

    private final LedgerAssembler ledgerAssembler = new LedgerAssembler();

    @Test
    @DisplayName("입력값으로 장부 상세 엔티티를 생성한다.")
    void toLedgerDetailEntity() {
        // given
        Ledger ledger = Ledger.builder().id(20L).totalBalance(10_000).build();
        User user = User.builder().id(1L).nickname("김머니").build();
        Category category = Category.of("식비", 10L);
        ZonedDateTime paymentDate = ZonedDateTime.of(2024, 3, 15, 12, 0, 0, 0, ZoneId.systemDefault());

        // when
        LedgerDetail result = ledgerAssembler.toLedgerDetailEntity(
                ledger, user, "편의점", FundType.EXPENSE, 4_500, 5_500, "간식 구매", paymentDate, category);

        // then
        assertThat(result.getId()).isNull();
        assertThat(result.getLedger()).isSameAs(ledger);
        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getStoreInfo()).isEqualTo("편의점");
        assertThat(result.getFundType()).isEqualTo(FundType.EXPENSE);
        assertThat(result.getAmount()).isEqualTo(4_500);
        assertThat(result.getBalance()).isEqualTo(5_500);
        assertThat(result.getDescription()).isEqualTo("간식 구매");
        assertThat(result.getPaymentDate()).isEqualTo(paymentDate);
        assertThat(result.getCategory()).isSameAs(category);
    }

    @Test
    @DisplayName("영수증 URL 목록을 같은 순서의 영수증 엔티티 목록으로 변환한다.")
    void toLedgerReceiptEntities() {
        // given
        LedgerDetail ledgerDetail = LedgerDetail.builder().id(50L).build();

        // when
        List<LedgerReceipt> result = ledgerAssembler.toLedgerReceiptEntities(
                ledgerDetail, List.of("https://img/r1.png", "https://img/r2.png"));

        // then
        assertThat(result)
                .extracting(LedgerReceipt::getReceiptImageUrl)
                .containsExactly("https://img/r1.png", "https://img/r2.png");
        assertThat(result).allSatisfy(receipt -> assertThat(receipt.getLedgerDetail()).isSameAs(ledgerDetail));
    }

    @Test
    @DisplayName("영수증 URL 목록이 null이면 빈 목록을 반환한다.")
    void toLedgerReceiptEntities_null() {
        // when & then
        assertThat(ledgerAssembler.toLedgerReceiptEntities(LedgerDetail.builder().build(), null)).isEmpty();
    }

    @Test
    @DisplayName("영수증 URL 목록이 비어 있으면 빈 목록을 반환한다.")
    void toLedgerReceiptEntities_empty() {
        // when & then
        assertThat(ledgerAssembler.toLedgerReceiptEntities(LedgerDetail.builder().build(), Collections.emptyList()))
                .isEmpty();
    }

    @Test
    @DisplayName("증빙 자료 URL 목록을 같은 순서의 증빙 자료 엔티티 목록으로 변환한다.")
    void toLedgerDocumentEntities() {
        // given
        LedgerDetail ledgerDetail = LedgerDetail.builder().id(50L).build();

        // when
        List<LedgerDocument> result = ledgerAssembler.toLedgerDocumentEntities(
                ledgerDetail, List.of("https://img/d1.png", "https://img/d2.png"));

        // then
        assertThat(result)
                .extracting(LedgerDocument::getDocumentImageUrl)
                .containsExactly("https://img/d1.png", "https://img/d2.png");
        assertThat(result).allSatisfy(document -> assertThat(document.getLedgerDetail()).isSameAs(ledgerDetail));
    }

    @Test
    @DisplayName("증빙 자료 URL 목록이 null이면 빈 목록을 반환한다.")
    void toLedgerDocumentEntities_null() {
        // when & then
        assertThat(ledgerAssembler.toLedgerDocumentEntities(LedgerDetail.builder().build(), null)).isEmpty();
    }
}
