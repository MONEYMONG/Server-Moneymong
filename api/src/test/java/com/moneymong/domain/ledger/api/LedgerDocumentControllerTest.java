package com.moneymong.domain.ledger.api;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.ledger.api.request.CreateLedgerDocumentRequest;
import com.moneymong.domain.ledger.service.manager.LedgerDocumentManager;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class LedgerDocumentControllerTest extends ControllerTestSupport {

    private static final Long DETAIL_ID = 30L;
    private static final Long DOCUMENT_ID = 300L;

    @Mock
    private LedgerDocumentManager ledgerDocumentManager;

    @Override
    protected Object controller() {
        return new LedgerDocumentController(ledgerDocumentManager);
    }

    @Test
    @DisplayName("장부 상세 내역에 증빙 자료를 등록한다.")
    void createLedgerDocument() throws Exception {
        List<String> urls = List.of("https://document/1", "https://document/2");

        mockMvc.perform(post("/api/v1/ledger-detail/{detailId}/ledger-document", DETAIL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateLedgerDocumentRequest(urls))))
                .andExpect(status().isOk());

        then(ledgerDocumentManager).should().createLedgerDocuments(DETAIL_ID, urls);
    }

    @Test
    @DisplayName("증빙 자료가 12개를 넘으면 400을 반환한다.")
    void createLedgerDocumentWithTooMany() throws Exception {
        mockMvc.perform(post("/api/v1/ledger-detail/{detailId}/ledger-document", DETAIL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateLedgerDocumentRequest(Collections.nCopies(13, "https://document")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"));

        then(ledgerDocumentManager).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("증빙 자료를 삭제한다.")
    void deleteLedgerDocument() throws Exception {
        mockMvc.perform(delete("/api/v1/ledger-detail/{detailId}/ledger-document/{documentId}", DETAIL_ID, DOCUMENT_ID))
                .andExpect(status().isOk());

        then(ledgerDocumentManager).should().removeLedgerDocuments(USER_ID, DETAIL_ID, DOCUMENT_ID);
    }

    @Test
    @DisplayName("존재하지 않는 증빙 자료를 삭제하면 404를 반환한다.")
    void deleteLedgerDocumentNotFound() throws Exception {
        willThrow(new NotFoundException(ErrorCode.LEDGER_DOCUMENT_NOT_FOUND))
                .given(ledgerDocumentManager).removeLedgerDocuments(USER_ID, DETAIL_ID, DOCUMENT_ID);

        mockMvc.perform(delete("/api/v1/ledger-detail/{detailId}/ledger-document/{documentId}", DETAIL_ID, DOCUMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER-007"));
    }
}
