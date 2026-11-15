package com.moneymong.domain.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.user.service.dto.request.LoginRequest;
import com.moneymong.domain.user.service.dto.response.LoginSuccessResponse;
import com.moneymong.domain.user.service.dto.response.UserProfileResponse;
import com.moneymong.domain.user.service.UserFacadeService;
import com.moneymong.domain.user.service.UserService;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class UserControllerTest extends ControllerTestSupport {

    @Mock
    private UserFacadeService userFacadeService;

    @Mock
    private UserService userService;

    @Override
    protected Object controller() {
        return new UserController(userFacadeService, userService);
    }

    @Test
    @DisplayName("OAuth 로그인에 성공하면 토큰과 학교 정보 여부를 반환한다.")
    void login() throws Exception {
        given(userFacadeService.login(any(LoginRequest.class)))
                .willReturn(LoginSuccessResponse.of("access", "refresh", true, false, true));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "provider", "KAKAO",
                                "accessToken", "kakao-token",
                                "name", "몽몽",
                                "code", "apple-code"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.loginSuccess").value(true))
                .andExpect(jsonPath("$.schoolInfoExist").value(false))
                .andExpect(jsonPath("$.schoolInfoProvided").value(true));

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        then(userFacadeService).should().login(captor.capture());
        LoginRequest request = captor.getValue();
        assertThat(request.getProvider()).isEqualTo("KAKAO");
        assertThat(request.getAccessToken()).isEqualTo("kakao-token");
        assertThat(request.getName()).isEqualTo("몽몽");
        assertThat(request.getCode()).isEqualTo("apple-code");
    }

    @Test
    @DisplayName("로그인 수단이 비어 있으면 400을 반환한다.")
    void loginWithoutProvider() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("provider", "", "accessToken", "kakao-token"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"));

        then(userFacadeService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("내 프로필을 조회한다.")
    void getMyProfile() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .id(USER_ID)
                .provider("KAKAO")
                .nickname("몽몽")
                .email("test@example.com")
                .universityName("머니몽대학교")
                .grade(2)
                .build();
        given(userService.getUserProfile(USER_ID)).willReturn(profile);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.provider").value("KAKAO"))
                .andExpect(jsonPath("$.nickname").value("몽몽"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.universityName").value("머니몽대학교"))
                .andExpect(jsonPath("$.grade").value(2));

        then(userService).should().getUserProfile(USER_ID);
    }

    @Test
    @DisplayName("존재하지 않는 회원의 프로필을 조회하면 404를 반환한다.")
    void getMyProfileNotFound() throws Exception {
        given(userService.getUserProfile(USER_ID)).willThrow(new NotFoundException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER-001"));
    }

    @Test
    @DisplayName("회원 탈퇴를 요청한다.")
    void deleteUser() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isOk());

        then(userFacadeService).should().delete(USER_ID);
    }
}
