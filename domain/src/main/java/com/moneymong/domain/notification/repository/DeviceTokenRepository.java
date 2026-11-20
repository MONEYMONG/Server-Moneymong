package com.moneymong.domain.notification.repository;

import com.moneymong.domain.notification.entity.DeviceToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByDeviceId(String deviceId);

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findAllByUserId(Long userId);

    void deleteByUserIdAndDeviceId(Long userId, String deviceId);

    void deleteAllByUserId(Long userId);

    /**
     * 발송 중에 같은 기기가 새 토큰으로 갱신됐을 수 있으므로, 발송에 쓴 토큰과 같을 때만 지운다.
     */
    @Modifying
    @Query("delete from DeviceToken d where d.id = :id and d.token = :token")
    int deleteByIdAndToken(@Param("id") Long id, @Param("token") String token);
}
