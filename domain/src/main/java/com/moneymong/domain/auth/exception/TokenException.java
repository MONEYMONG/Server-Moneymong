package com.moneymong.domain.auth.exception;

import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.enums.ErrorCode;

public class TokenException extends BusinessException {
    public TokenException(ErrorCode errorCode) {
        super(errorCode);
    }
}
