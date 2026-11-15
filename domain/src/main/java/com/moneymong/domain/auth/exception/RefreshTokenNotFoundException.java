package com.moneymong.domain.auth.exception;

import com.moneymong.global.exception.enums.ErrorCode;

public class RefreshTokenNotFoundException extends TokenException {
    public RefreshTokenNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
