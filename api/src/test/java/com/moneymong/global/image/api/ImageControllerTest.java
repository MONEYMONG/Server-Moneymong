package com.moneymong.global.image.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.global.image.dto.ImageDeleteRequest;
import com.moneymong.global.image.dto.ImageResponse;
import com.moneymong.global.image.exception.ImageNotExistsException;
import com.moneymong.global.image.service.ImageService;
import com.moneymong.support.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ImageControllerTest extends ControllerTestSupport {

    @Mock
    private ImageService imageService;

    @Override
    protected Object controller() {
        return new ImageController(imageService);
    }

    @Test
    @DisplayName("이미지 파일을 업로드하면 저장된 key와 경로를 반환한다.")
    void upload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
        given(imageService.upload(any(MultipartFile.class)))
                .willReturn(new ImageResponse("receipt-key", "https://storage/receipt-key"));

        mockMvc.perform(multipart("/api/v1/images").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("receipt-key"))
                .andExpect(jsonPath("$.path").value("https://storage/receipt-key"));

        ArgumentCaptor<MultipartFile> captor = ArgumentCaptor.forClass(MultipartFile.class);
        then(imageService).should().upload(captor.capture());
        assertThat(captor.getValue().getOriginalFilename()).isEqualTo("receipt.png");
        assertThat(captor.getValue().getBytes()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("이미지를 삭제한다.")
    void remove() throws Exception {
        mockMvc.perform(delete("/api/v1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ImageDeleteRequest("receipt-key", "https://storage/receipt-key"))))
                .andExpect(status().isOk());

        ArgumentCaptor<ImageDeleteRequest> captor = ArgumentCaptor.forClass(ImageDeleteRequest.class);
        then(imageService).should().remove(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("receipt-key");
        assertThat(captor.getValue().getPath()).isEqualTo("https://storage/receipt-key");
    }

    @Test
    @DisplayName("존재하지 않는 이미지를 삭제하면 404를 반환한다.")
    void removeNotExists() throws Exception {
        willThrow(new ImageNotExistsException(ErrorCode.IMAGE_NOT_EXISTS))
                .given(imageService).remove(any(ImageDeleteRequest.class));

        mockMvc.perform(delete("/api/v1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ImageDeleteRequest("unknown", "https://storage/unknown"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE-001"));
    }
}
