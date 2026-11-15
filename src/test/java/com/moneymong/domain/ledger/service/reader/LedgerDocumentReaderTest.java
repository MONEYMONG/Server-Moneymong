package com.moneymong.domain.ledger.service.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.moneymong.domain.ledger.entity.LedgerDocument;
import com.moneymong.domain.ledger.repository.LedgerDocumentRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerDocumentReaderTest {

    private static final Long LEDGER_DETAIL_ID = 50L;

    @Mock
    private LedgerDocumentRepository ledgerDocumentRepository;

    @InjectMocks
    private LedgerDocumentReader ledgerDocumentReader;

    @Test
    @DisplayName("장부 상세 ID로 증빙 자료 목록을 조회한다.")
    void getLedgerDocuments_success() {
        // given
        List<LedgerDocument> documents = List.of(
                LedgerDocument.builder().id(1L).documentImageUrl("https://img/d1.png").build()
        );
        given(ledgerDocumentRepository.findByLedgerDetailId(LEDGER_DETAIL_ID)).willReturn(documents);

        // when
        List<LedgerDocument> result = ledgerDocumentReader.getLedgerDocuments(LEDGER_DETAIL_ID);

        // then
        assertThat(result)
                .extracting(LedgerDocument::getDocumentImageUrl)
                .containsExactly("https://img/d1.png");
    }

    @Test
    @DisplayName("증빙 자료가 없으면 빈 목록을 반환한다.")
    void getLedgerDocuments_empty() {
        // given
        given(ledgerDocumentRepository.findByLedgerDetailId(LEDGER_DETAIL_ID)).willReturn(Collections.emptyList());

        // when & then
        assertThat(ledgerDocumentReader.getLedgerDocuments(LEDGER_DETAIL_ID)).isEmpty();
    }
}
