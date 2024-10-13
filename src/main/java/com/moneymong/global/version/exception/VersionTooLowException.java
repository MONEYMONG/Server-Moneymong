package com.moneymong.global.version.exception;

import com.moneymong.global.exception.custom.BusinessException;
import com.moneymong.global.exception.enums.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class VersionTooLowException extends BusinessException {

    public VersionTooLowException(ErrorCode errorCode) {
        super(errorCode);
    }
}
