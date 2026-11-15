package com.moneymong.domain.university.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.BDDMockito.given;

import com.moneymong.domain.university.University;
import com.moneymong.domain.university.service.dto.response.SearchUniversityResponse;
import com.moneymong.domain.university.service.dto.response.UniversityResponse;
import com.moneymong.domain.university.repository.UniversityRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UniversityServiceTest {

    @Mock
    private UniversityRepository universityRepository;

    @InjectMocks
    private UniversityService universityService;

    @Test
    @DisplayName("키워드로 대학교를 검색하면 아이디와 학교명 목록을 반환한다.")
    void findByKeyword() {
        // given
        given(universityRepository.findByKeyword("머니"))
                .willReturn(List.of(university(1L, "머니몽대학교"), university(2L, "머니대학교")));

        // when
        SearchUniversityResponse response = universityService.findByKeyword("머니");

        // then
        assertThat(response.getUniversities())
                .extracting(UniversityResponse::getId, UniversityResponse::getSchoolName)
                .containsExactly(tuple(1L, "머니몽대학교"), tuple(2L, "머니대학교"));
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 목록을 반환한다.")
    void findByKeywordEmpty() {
        // given
        given(universityRepository.findByKeyword("없는학교")).willReturn(List.of());

        // when
        SearchUniversityResponse response = universityService.findByKeyword("없는학교");

        // then
        assertThat(response.getUniversities()).isEmpty();
    }

    private University university(Long id, String schoolName) {
        University university = new University();
        ReflectionTestUtils.setField(university, "id", id);
        ReflectionTestUtils.setField(university, "schoolName", schoolName);
        return university;
    }
}
