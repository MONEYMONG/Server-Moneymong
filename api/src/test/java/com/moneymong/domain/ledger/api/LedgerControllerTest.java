package com.moneymong.domain.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.ledger.service.dto.request.CreateLedgerRequest;
import com.moneymong.domain.ledger.service.dto.request.UpdateLedgerRequest;
import com.moneymong.domain.ledger.service.dto.response.LedgerDetailInfoView;
import com.moneymong.domain.ledger.service.dto.response.LedgerDocumentInfoView;
import com.moneymong.domain.ledger.service.dto.response.LedgerReceiptInfoView;
import com.moneymong.domain.ledger.service.dto.response.ledger.LedgerInfoView;
import com.moneymong.domain.ledger.service.dto.response.ledger.LedgerInfoViewDetail;
import com.moneymong.domain.ledger.service.dto.response.report.LedgerReportResponse;
import com.moneymong.domain.ledger.entity.enums.FundType;
import com.moneymong.domain.ledger.service.manager.LedgerService;
import com.moneymong.domain.ledger.service.reader.LedgerReader;
import com.moneymong.domain.ledger.service.reader.LedgerReportReader;
import com.moneymong.global.exception.custom.BadRequestException;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class LedgerControllerTest extends ControllerTestSupport {

    private static final Long LEDGER_ID = 3L;
    private static final Long DETAIL_ID = 30L;
    private static final Long AGENCY_ID = 10L;
    private static final String PAYMENT_DATE = "2024-03-01T10:00:00+09:00";

    @Mock
    private LedgerService ledgerService;

    @Mock
    private LedgerReader ledgerReader;

    @Mock
    private LedgerReportReader ledgerReportReader;

    @Override
    protected Object controller() {
        return new LedgerController(ledgerService, ledgerReader, ledgerReportReader);
    }

    private LedgerDetailInfoView detailView() {
        return LedgerDetailInfoView.builder()
                .id(DETAIL_ID)
                .storeInfo("편의점")
                .amount(5000)
                .fundType(FundType.EXPENSE)
                .description("간식")
                .paymentDate(ZonedDateTime.parse(PAYMENT_DATE))
                .receiptImageUrls(List.of(LedgerReceiptInfoView.from(1L, "https://receipt")))
                .documentImageUrls(List.of(LedgerDocumentInfoView.from(2L, "https://document")))
                .authorName("몽몽")
                .category("간식비")
                .build();
    }

    private LedgerInfoView ledgerInfoView() {
        LedgerInfoViewDetail detail = LedgerInfoViewDetail.builder()
                .id(DETAIL_ID)
                .storeInfo("편의점")
                .fundType(FundType.EXPENSE)
                .amount(5000)
                .balance(15000)
                .order(1)
                .build();
        return LedgerInfoView.builder()
                .id(LEDGER_ID)
                .totalBalance(15000)
                .ledgerInfoViewDetails(List.of(detail))
                .build();
    }

    private Map<String, Object> createBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("storeInfo", "편의점");
        body.put("fundType", "EXPENSE");
        body.put("amount", 5000);
        body.put("description", "간식");
        body.put("paymentDate", PAYMENT_DATE);
        body.put("receiptImageUrls", List.of("https://receipt"));
        body.put("documentImageUrls", List.of("https://document"));
        return body;
    }

    private Map<String, Object> updateBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("storeInfo", "카페");
        body.put("amount", 4000);
        body.put("description", "커피");
        body.put("paymentDate", PAYMENT_DATE);
        return body;
    }

    @Test
    @DisplayName("장부 내역을 등록하면 등록된 상세 내역을 반환한다.")
    void createLedger() throws Exception {
        given(ledgerService.createLedger(eq(USER_ID), eq(LEDGER_ID), any(CreateLedgerRequest.class)))
                .willReturn(detailView());

        mockMvc.perform(post("/api/v1/ledger/{id}", LEDGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(createBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DETAIL_ID))
                .andExpect(jsonPath("$.storeInfo").value("편의점"))
                .andExpect(jsonPath("$.amount").value(5000))
                .andExpect(jsonPath("$.fundType").value("EXPENSE"))
                .andExpect(jsonPath("$.receiptImageUrls[0].receiptImageUrl").value("https://receipt"))
                .andExpect(jsonPath("$.documentImageUrls[0].documentImageUrl").value("https://document"))
                .andExpect(jsonPath("$.authorName").value("몽몽"))
                .andExpect(jsonPath("$.category").value("간식비"));

        ArgumentCaptor<CreateLedgerRequest> captor = ArgumentCaptor.forClass(CreateLedgerRequest.class);
        then(ledgerService).should().createLedger(eq(USER_ID), eq(LEDGER_ID), captor.capture());
        CreateLedgerRequest request = captor.getValue();
        assertThat(request.getStoreInfo()).isEqualTo("편의점");
        assertThat(request.getFundType()).isEqualTo(FundType.EXPENSE);
        assertThat(request.getAmount()).isEqualTo(5000);
        assertThat(request.getPaymentDate().toInstant()).isEqualTo(ZonedDateTime.parse(PAYMENT_DATE).toInstant());
        assertThat(request.getReceiptImageUrls()).containsExactly("https://receipt");
    }

    @Test
    @DisplayName("필수 값이 빠진 장부 내역 등록 요청은 400을 반환한다.")
    void createLedgerWithInvalidBody() throws Exception {
        Map<String, Object> body = createBody();
        body.remove("amount");
        body.put("storeInfo", "");

        mockMvc.perform(post("/api/v1/ledger/{id}", LEDGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"))
                .andExpect(jsonPath("$.messages.length()").value(2));

        then(ledgerService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("총 잔액 한도를 넘는 장부 내역 등록은 400을 반환한다.")
    void createLedgerOverflow() throws Exception {
        given(ledgerService.createLedger(eq(USER_ID), eq(LEDGER_ID), any(CreateLedgerRequest.class)))
                .willThrow(new BadRequestException(ErrorCode.LEDGER_AMOUNT_OVERFLOW));

        mockMvc.perform(post("/api/v1/ledger/{id}", LEDGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(createBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEDGER-005"));
    }

    @Test
    @DisplayName("장부 상세 내역을 수정한다.")
    void updateLedger() throws Exception {
        given(ledgerService.updateLedger(eq(USER_ID), eq(DETAIL_ID), any(UpdateLedgerRequest.class)))
                .willReturn(detailView());

        mockMvc.perform(put("/api/v1/ledger/ledger-detail/{detailId}", DETAIL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DETAIL_ID));

        ArgumentCaptor<UpdateLedgerRequest> captor = ArgumentCaptor.forClass(UpdateLedgerRequest.class);
        then(ledgerService).should().updateLedger(eq(USER_ID), eq(DETAIL_ID), captor.capture());
        assertThat(captor.getValue().getStoreInfo()).isEqualTo("카페");
        assertThat(captor.getValue().getAmount()).isEqualTo(4000);
    }

    @Test
    @DisplayName("storeInfo가 20자를 넘으면 수정 요청은 400을 반환한다.")
    void updateLedgerWithTooLongStoreInfo() throws Exception {
        Map<String, Object> body = updateBody();
        body.put("storeInfo", "가".repeat(21));

        mockMvc.perform(put("/api/v1/ledger/ledger-detail/{detailId}", DETAIL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"));

        then(ledgerService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("연월과 페이지 조건으로 장부 내역을 조회한다.")
    void search() throws Exception {
        given(ledgerReader.search(USER_ID, LEDGER_ID, 2024, 3, 0, 20)).willReturn(ledgerInfoView());

        mockMvc.perform(get("/api/v1/ledger/{id}", LEDGER_ID)
                        .param("year", "2024")
                        .param("month", "3")
                        .param("page", "0")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(LEDGER_ID))
                .andExpect(jsonPath("$.totalBalance").value(15000))
                .andExpect(jsonPath("$.ledgerInfoViewDetails[0].id").value(DETAIL_ID))
                .andExpect(jsonPath("$.ledgerInfoViewDetails[0].balance").value(15000));

        then(ledgerReader).should().search(USER_ID, LEDGER_ID, 2024, 3, 0, 20);
    }

    @Test
    @DisplayName("month가 12를 넘으면 장부 조회는 400을 반환한다.")
    void searchWithInvalidMonth() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/{id}", LEDGER_ID)
                        .param("year", "2024")
                        .param("month", "13")
                        .param("page", "0")
                        .param("limit", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"));

        then(ledgerReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("장부 접근 권한이 없으면 403을 반환한다.")
    void searchForbidden() throws Exception {
        given(ledgerReader.search(USER_ID, LEDGER_ID, 2024, 3, 0, 20))
                .willThrow(new InvalidAccessException(ErrorCode.INVALID_LEDGER_ACCESS));

        mockMvc.perform(get("/api/v1/ledger/{id}", LEDGER_ID)
                        .param("year", "2024")
                        .param("month", "3")
                        .param("page", "0")
                        .param("limit", "20"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEDGER-002"));
    }

    @Test
    @DisplayName("입출금 유형 필터로 장부 내역을 조회한다.")
    void searchByFilter() throws Exception {
        given(ledgerReader.searchByFilter(USER_ID, LEDGER_ID, 2024, 3, 1, 10, FundType.INCOME))
                .willReturn(ledgerInfoView());

        mockMvc.perform(get("/api/v1/ledger/{id}/filter", LEDGER_ID)
                        .param("year", "2024")
                        .param("month", "3")
                        .param("page", "1")
                        .param("limit", "10")
                        .param("fundType", "INCOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(LEDGER_ID));

        then(ledgerReader).should().searchByFilter(USER_ID, LEDGER_ID, 2024, 3, 1, 10, FundType.INCOME);
    }

    @Test
    @DisplayName("입출금 유형 없이 필터 조회하면 400을 반환한다.")
    void searchByFilterWithoutFundType() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/{id}/filter", LEDGER_ID)
                        .param("year", "2024")
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"));

        then(ledgerReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소속 ID로 장부 내역을 조회한다.")
    void searchByAgency() throws Exception {
        given(ledgerReader.searchByAgency(USER_ID, AGENCY_ID, 2024, null, 0, 20)).willReturn(ledgerInfoView());

        mockMvc.perform(get("/api/v1/ledger/agencies/{agencyId}", AGENCY_ID)
                        .param("year", "2024")
                        .param("page", "0")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(LEDGER_ID));

        then(ledgerReader).should().searchByAgency(USER_ID, AGENCY_ID, 2024, null, 0, 20);
    }

    @Test
    @DisplayName("소속에 장부가 존재하는지 조회한다.")
    void existByAgency() throws Exception {
        given(ledgerReader.exists(AGENCY_ID)).willReturn(true);

        mockMvc.perform(get("/api/v1/ledger/agencies/{agencyId}/exist", AGENCY_ID))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        then(ledgerReader).should().exists(AGENCY_ID);
    }

    @Test
    @DisplayName("기간을 지정해 소속 장부 리포트를 조회한다.")
    void getReport() throws Exception {
        LedgerReportResponse report = LedgerReportResponse.builder()
                .agencyId(AGENCY_ID)
                .agencyName("머니몽")
                .period(new LedgerReportResponse.PeriodInfo(2024, 1, 2024, 3))
                .totalIncome(30000)
                .totalExpense(10000)
                .totalBalance(20000)
                .monthly(List.of())
                .build();
        given(ledgerReportReader.getReport(USER_ID, AGENCY_ID, 2024, 1, 2024, 3)).willReturn(report);

        mockMvc.perform(get("/api/v1/ledger/agencies/{agencyId}/reports", AGENCY_ID)
                        .param("startYear", "2024")
                        .param("startMonth", "1")
                        .param("endYear", "2024")
                        .param("endMonth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyId").value(AGENCY_ID))
                .andExpect(jsonPath("$.agencyName").value("머니몽"))
                .andExpect(jsonPath("$.period.startMonth").value(1))
                .andExpect(jsonPath("$.period.endMonth").value(3))
                .andExpect(jsonPath("$.totalIncome").value(30000))
                .andExpect(jsonPath("$.totalExpense").value(10000))
                .andExpect(jsonPath("$.totalBalance").value(20000));

        then(ledgerReportReader).should().getReport(USER_ID, AGENCY_ID, 2024, 1, 2024, 3);
    }

    @Test
    @DisplayName("리포트 기간의 월이 범위를 벗어나면 400을 반환한다.")
    void getReportWithInvalidMonth() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/agencies/{agencyId}/reports", AGENCY_ID)
                        .param("startYear", "2024")
                        .param("startMonth", "0")
                        .param("endYear", "2024")
                        .param("endMonth", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"));

        then(ledgerReportReader).shouldHaveNoInteractions();
    }
}
