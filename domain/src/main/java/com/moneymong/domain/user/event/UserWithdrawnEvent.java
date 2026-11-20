package com.moneymong.domain.user.event;

/**
 * 회원 탈퇴 처리 중에 발행된다.
 *
 * <p>listener는 탈퇴와 같은 transaction 안에서 동기로 실행된다.
 * user가 다른 도메인을 직접 호출하지 않도록, 회원에 딸린 데이터(디바이스 토큰 등)는 이 이벤트를 받아 정리한다.
 */
public record UserWithdrawnEvent(Long userId) {
}
