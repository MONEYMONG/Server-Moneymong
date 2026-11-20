package com.moneymong.domain.notification.service;

import com.moneymong.domain.notification.entity.DeviceToken;
import com.moneymong.domain.notification.repository.DeviceTokenRepository;
import com.moneymong.domain.notification.service.dto.request.RegisterDeviceTokenRequest;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * 기기의 토큰을 등록하거나 갱신한다. 같은 요청을 여러 번 보내도 결과가 같다.
     *
     * <p>로그인 직후, 앱 실행 시, FCM 토큰이 갱신될 때 앱이 호출한다.
     */
    @Transactional
    public void register(Long userId, RegisterDeviceTokenRequest request) {
        String deviceId = request.getDeviceId();
        String token = request.getToken();
        ZonedDateTime now = ZonedDateTime.now();

        // 같은 토큰이 다른 기기 행에 남아 있으면 먼저 지운다(앱 재설치로 deviceId만 새로 생긴 경우 등).
        // Hibernate는 flush 때 insert·update를 delete보다 먼저 실행하므로, unique 제약에 걸리지 않게 바로 flush한다.
        deviceTokenRepository.findByToken(token)
                .filter(existing -> !existing.getDeviceId().equals(deviceId))
                .ifPresent(existing -> {
                    deviceTokenRepository.delete(existing);
                    deviceTokenRepository.flush();
                });

        deviceTokenRepository.findByDeviceId(deviceId)
                .ifPresentOrElse(
                        deviceToken -> deviceToken.refresh(userId, token, request.getPlatform(), now),
                        () -> deviceTokenRepository.save(
                                DeviceToken.of(userId, deviceId, token, request.getPlatform(), now)
                        )
                );
    }

    /**
     * 내 기기의 토큰을 지운다. 없거나 다른 계정의 기기면 아무것도 하지 않는다(로그아웃이 실패하지 않도록).
     */
    @Transactional
    public void unregister(Long userId, String deviceId) {
        deviceTokenRepository.deleteByUserIdAndDeviceId(userId, deviceId);
    }

    @Transactional
    public void deleteAll(Long userId) {
        deviceTokenRepository.deleteAllByUserId(userId);
    }
}
