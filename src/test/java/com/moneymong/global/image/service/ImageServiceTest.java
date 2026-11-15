package com.moneymong.global.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.global.image.dto.ImageDeleteRequest;
import com.moneymong.global.image.dto.ImageResponse;
import com.moneymong.global.image.exception.FileIOException;
import com.moneymong.global.image.exception.ImageNotExistsException;
import com.moneymong.global.image.handler.ImageStorageHandler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private ImageStorageHandler imageStorageHandler;

    @InjectMocks
    private ImageService imageService;

    private String fileName;

    @BeforeEach
    void setUp() {
        // ImageService는 user.dir 아래에 원본 파일명으로 임시 파일을 만든다. 테스트마다 고유한 이름을 쓰고 끝나면 지운다.
        fileName = "image-service-test-" + UUID.randomUUID() + ".png";
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(new File(System.getProperty("user.dir"), fileName).toPath());
    }

    @Test
    @DisplayName("업로드한 파일을 임시 파일로 변환해 저장소에 올리고 결과를 반환한다.")
    void upload() throws IOException {
        byte[] content = {1, 2, 3, 4};
        MockMultipartFile multipartFile = new MockMultipartFile("file", fileName, "image/png", content);
        ImageResponse expected = new ImageResponse("key", "https://storage/key");
        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        byte[][] uploadedContent = new byte[1][];
        given(imageStorageHandler.upload(captor.capture())).willAnswer(invocation -> {
            uploadedContent[0] = Files.readAllBytes(((File) invocation.getArgument(0)).toPath());
            return expected;
        });

        ImageResponse response = imageService.upload(multipartFile);

        assertThat(response).isSameAs(expected);
        assertThat(captor.getValue().getName()).isEqualTo(fileName);
        assertThat(uploadedContent[0]).containsExactly(content);
    }

    @Test
    @DisplayName("업로드 파일을 읽지 못하면 FileIOException이 발생하고 저장소에 올리지 않는다.")
    void uploadFailsWhenReadingFile() throws IOException {
        MultipartFile multipartFile = mock(MultipartFile.class);
        given(multipartFile.getOriginalFilename()).willReturn(fileName);
        given(multipartFile.getBytes()).willThrow(new IOException("read failed"));

        assertThatThrownBy(() -> imageService.upload(multipartFile))
                .isInstanceOf(FileIOException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_IO_EXCEPTION);

        then(imageStorageHandler).should(never()).upload(any());
    }

    @Test
    @DisplayName("저장소에 존재하는 이미지를 삭제한다.")
    void remove() {
        given(imageStorageHandler.doesObjectExists("key")).willReturn(true);

        imageService.remove(new ImageDeleteRequest("key", "https://storage/key"));

        then(imageStorageHandler).should().remove("key");
    }

    @Test
    @DisplayName("저장소에 없는 이미지를 삭제하면 ImageNotExistsException이 발생한다.")
    void removeNotExists() {
        given(imageStorageHandler.doesObjectExists("unknown")).willReturn(false);

        assertThatThrownBy(() -> imageService.remove(new ImageDeleteRequest("unknown", "https://storage/unknown")))
                .isInstanceOf(ImageNotExistsException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_NOT_EXISTS);

        then(imageStorageHandler).should(never()).remove(anyString());
    }
}
