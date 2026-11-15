package com.moneymong.domain.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.user.service.dto.request.CreateUserUniversityRequest;
import com.moneymong.domain.user.service.dto.request.UpdateUserUniversityRequest;
import com.moneymong.domain.user.service.dto.response.UserUniversityResponse;
import com.moneymong.domain.user.service.UserUniversityService;
import com.moneymong.global.exception.custom.BadRequestException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.support.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class UserUniversityControllerTest extends ControllerTestSupport {

    @Mock
    private UserUniversityService userUniversityService;

    @Override
    protected Object controller() {
        return new UserUniversityController(userUniversityService);
    }

    @Test
    @DisplayName("회원의 대학 정보를 조회한다.")
    void getUserUniversity() throws Exception {
        given(userUniversityService.get(USER_ID)).willReturn(UserUniversityResponse.of("머니몽대학교", 3));

        mockMvc.perform(get("/api/v1/user-university"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universityName").value("머니몽대학교"))
                .andExpect(jsonPath("$.grade").value(3));

        then(userUniversityService).should().get(USER_ID);
    }

    @Test
    @DisplayName("회원의 대학 정보가 없으면 404를 반환한다.")
    void getUserUniversityNotFound() throws Exception {
        given(userUniversityService.get(USER_ID))
                .willThrow(new NotFoundException(ErrorCode.USER_UNIVERSITY_NOT_FOUND));

        mockMvc.perform(get("/api/v1/user-university"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNIVERSITY-001"));
    }

    @Test
    @DisplayName("회원의 대학 정보를 등록한다.")
    void createUserUniversity() throws Exception {
        mockMvc.perform(post("/api/v1/user-university")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateUserUniversityRequest("머니몽대학교", 1))))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateUserUniversityRequest> captor = ArgumentCaptor.forClass(CreateUserUniversityRequest.class);
        then(userUniversityService).should().create(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getUniversityName()).isEqualTo("머니몽대학교");
        assertThat(captor.getValue().getGrade()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 대학 정보가 있는 회원이 등록하면 400을 반환한다.")
    void createUserUniversityAlreadyExists() throws Exception {
        willThrow(new BadRequestException(ErrorCode.USER_UNIVERSITY_ALREADY_EXISTS))
                .given(userUniversityService).create(eq(USER_ID), any(CreateUserUniversityRequest.class));

        mockMvc.perform(post("/api/v1/user-university")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CreateUserUniversityRequest("머니몽대학교", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNIVERSITY-002"));
    }

    @Test
    @DisplayName("회원의 대학 정보를 수정한다.")
    void updateUserUniversity() throws Exception {
        mockMvc.perform(patch("/api/v1/user-university")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new UpdateUserUniversityRequest("몽몽대학교", 4))))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateUserUniversityRequest> captor = ArgumentCaptor.forClass(UpdateUserUniversityRequest.class);
        then(userUniversityService).should().update(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getUniversityName()).isEqualTo("몽몽대학교");
        assertThat(captor.getValue().getGrade()).isEqualTo(4);
    }

    @Test
    @DisplayName("회원의 대학 정보를 삭제한다.")
    void deleteUserUniversity() throws Exception {
        mockMvc.perform(delete("/api/v1/user-university"))
                .andExpect(status().isOk());

        then(userUniversityService).should().delete(USER_ID);
    }
}
