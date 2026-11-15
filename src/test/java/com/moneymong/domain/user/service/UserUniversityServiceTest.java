package com.moneymong.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.moneymong.domain.user.api.request.CreateUserUniversityRequest;
import com.moneymong.domain.user.api.request.UpdateUserUniversityRequest;
import com.moneymong.domain.user.api.response.UserUniversityResponse;
import com.moneymong.domain.user.entity.UserUniversity;
import com.moneymong.domain.user.repository.UserUniversityRepository;
import com.moneymong.global.exception.custom.BadRequestException;
import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserUniversityServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserUniversityRepository userUniversityRepository;

    @InjectMocks
    private UserUniversityService userUniversityService;

    @Test
    @DisplayName("회원의 대학 정보 존재 여부를 반환한다.")
    void exists() {
        // given
        given(userUniversityRepository.existsByUserId(USER_ID)).willReturn(true);

        // when & then
        assertThat(userUniversityService.exists(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("회원의 대학 정보를 조회한다.")
    void get() {
        // given
        given(userUniversityRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(UserUniversity.of(USER_ID, "머니몽대학교", 3)));

        // when
        UserUniversityResponse response = userUniversityService.get(USER_ID);

        // then
        assertThat(response.getUniversityName()).isEqualTo("머니몽대학교");
        assertThat(response.getGrade()).isEqualTo(3);
    }

    @Test
    @DisplayName("대학 정보가 없는 회원의 대학 정보를 조회하면 예외가 발생한다.")
    void getNotFound() {
        // given
        given(userUniversityRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userUniversityService.get(USER_ID))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_UNIVERSITY_NOT_FOUND);
    }

    @Test
    @DisplayName("회원의 대학 정보를 수정한다.")
    void update() {
        // given
        UserUniversity userUniversity = UserUniversity.of(USER_ID, "머니몽대학교", 1);
        given(userUniversityRepository.findByUserId(USER_ID)).willReturn(Optional.of(userUniversity));

        // when
        userUniversityService.update(USER_ID, new UpdateUserUniversityRequest("새대학교", 4));

        // then
        assertThat(userUniversity.getUniversityName()).isEqualTo("새대학교");
        assertThat(userUniversity.getGrade()).isEqualTo(4);
    }

    @Test
    @DisplayName("대학 정보가 없는 회원이 대학 정보를 수정하면 예외가 발생한다.")
    void updateNotFound() {
        // given
        given(userUniversityRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userUniversityService.update(USER_ID, new UpdateUserUniversityRequest("새대학교", 4)))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_UNIVERSITY_NOT_FOUND);
    }

    @Test
    @DisplayName("대학 정보가 없는 회원은 대학 정보를 등록할 수 있다.")
    void create() {
        // given
        given(userUniversityRepository.existsByUserId(USER_ID)).willReturn(false);

        // when
        userUniversityService.create(USER_ID, new CreateUserUniversityRequest("머니몽대학교", 2));

        // then
        ArgumentCaptor<UserUniversity> captor = ArgumentCaptor.forClass(UserUniversity.class);
        then(userUniversityRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getUniversityName()).isEqualTo("머니몽대학교");
        assertThat(captor.getValue().getGrade()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 대학 정보가 있는 회원이 다시 등록하면 예외가 발생한다.")
    void createDuplicated() {
        // given
        given(userUniversityRepository.existsByUserId(USER_ID)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userUniversityService.create(USER_ID, new CreateUserUniversityRequest("머니몽대학교", 2)))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_UNIVERSITY_ALREADY_EXISTS);
        then(userUniversityRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("대학 정보가 있으면 삭제한다.")
    void delete() {
        // given
        UserUniversity userUniversity = UserUniversity.of(USER_ID, "머니몽대학교", 1);
        given(userUniversityRepository.findByUserId(USER_ID)).willReturn(Optional.of(userUniversity));

        // when
        userUniversityService.delete(USER_ID);

        // then
        then(userUniversityRepository).should().delete(userUniversity);
    }

    @Test
    @DisplayName("대학 정보가 없으면 삭제하지 않는다.")
    void deleteWithoutUniversity() {
        // given
        given(userUniversityRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // when
        userUniversityService.delete(USER_ID);

        // then
        then(userUniversityRepository).should(never()).delete(any());
    }
}
