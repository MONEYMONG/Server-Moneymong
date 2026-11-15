package com.moneymong.domain.invitationcode.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.invitationcode.service.dto.request.CertifyInvitationCodeRequest;
import com.moneymong.domain.invitationcode.service.dto.response.CertifyInvitationCodeResponse;
import com.moneymong.domain.invitationcode.service.InvitationCodeService;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class InviteCodeControllerV2Test extends ControllerTestSupport {

    @Mock
    private InvitationCodeService invitationCodeService;

    @Override
    protected Object controller() {
        return new InviteCodeControllerV2(invitationCodeService);
    }

    @Test
    @DisplayName("소속 ID 없이 초대코드만으로 인증하면 해당 소속 ID를 반환한다.")
    void certifyV2() throws Exception {
        given(invitationCodeService.certifyV2(any(CertifyInvitationCodeRequest.class), eq(USER_ID)))
                .willReturn(CertifyInvitationCodeResponse.from(true, 77L));

        mockMvc.perform(post("/api/v2/agencies/invitation-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CertifyInvitationCodeRequest("123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certified").value(true))
                .andExpect(jsonPath("$.agencyId").value(77));

        ArgumentCaptor<CertifyInvitationCodeRequest> captor = ArgumentCaptor.forClass(CertifyInvitationCodeRequest.class);
        then(invitationCodeService).should().certifyV2(captor.capture(), eq(USER_ID));
        assertThat(captor.getValue().getInvitationCode()).isEqualTo("123456");
    }

    @Test
    @DisplayName("초대코드가 없으면 400을 반환한다.")
    void certifyV2WithoutCode() throws Exception {
        mockMvc.perform(post("/api/v2/agencies/invitation-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"));

        then(invitationCodeService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("초대코드에 해당하는 소속이 없으면 404를 반환한다.")
    void certifyV2AgencyNotFound() throws Exception {
        given(invitationCodeService.certifyV2(any(CertifyInvitationCodeRequest.class), eq(USER_ID)))
                .willThrow(new NotFoundException(ErrorCode.INVITATION_AGENCY_NOT_FOUND));

        mockMvc.perform(post("/api/v2/agencies/invitation-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CertifyInvitationCodeRequest("999999"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION-003"));
    }
}
