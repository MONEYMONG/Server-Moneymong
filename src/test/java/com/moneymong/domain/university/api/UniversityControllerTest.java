package com.moneymong.domain.university.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.university.api.response.SearchUniversityResponse;
import com.moneymong.domain.university.api.response.UniversityResponse;
import com.moneymong.domain.university.service.UniversityService;
import com.moneymong.support.ControllerTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class UniversityControllerTest extends ControllerTestSupport {

    @Mock
    private UniversityService universityService;

    @Override
    protected Object controller() {
        return new UniversityController(universityService);
    }

    @Test
    @DisplayName("키워드로 대학교를 검색한다.")
    void findByKeyword() throws Exception {
        given(universityService.findByKeyword("머니"))
                .willReturn(new SearchUniversityResponse(List.of(new UniversityResponse(1L, "머니몽대학교"))));

        mockMvc.perform(get("/api/v1/universities").param("keyword", "머니"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universities[0].id").value(1))
                .andExpect(jsonPath("$.universities[0].schoolName").value("머니몽대학교"));
    }
}
