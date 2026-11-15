package com.moneymong.domain.ledger.service.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.moneymong.domain.ledger.entity.LedgerReceipt;
import com.moneymong.domain.ledger.repository.LedgerReceiptRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerReceiptReaderTest {

    private static final Long LEDGER_DETAIL_ID = 50L;

    @Mock
    private LedgerReceiptRepository ledgerReceiptRepository;

    @InjectMocks
    private LedgerReceiptReader ledgerReceiptReader;

    @Test
    @DisplayName("장부 상세 ID로 영수증 목록을 조회한다.")
    void getLedgerReceipts_success() {
        // given
        List<LedgerReceipt> receipts = List.of(
                LedgerReceipt.builder().id(1L).receiptImageUrl("https://img/r1.png").build(),
                LedgerReceipt.builder().id(2L).receiptImageUrl("https://img/r2.png").build()
        );
        given(ledgerReceiptRepository.findByLedgerDetailId(LEDGER_DETAIL_ID)).willReturn(receipts);

        // when
        List<LedgerReceipt> result = ledgerReceiptReader.getLedgerReceipts(LEDGER_DETAIL_ID);

        // then
        assertThat(result)
                .extracting(LedgerReceipt::getReceiptImageUrl)
                .containsExactly("https://img/r1.png", "https://img/r2.png");
    }

    @Test
    @DisplayName("영수증이 없으면 빈 목록을 반환한다.")
    void getLedgerReceipts_empty() {
        // given
        given(ledgerReceiptRepository.findByLedgerDetailId(LEDGER_DETAIL_ID)).willReturn(Collections.emptyList());

        // when & then
        assertThat(ledgerReceiptReader.getLedgerReceipts(LEDGER_DETAIL_ID)).isEmpty();
    }
}
