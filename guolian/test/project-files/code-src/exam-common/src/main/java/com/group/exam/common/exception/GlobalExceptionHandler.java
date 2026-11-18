package com.group.exam.common.exception;

import com.group.exam.common.core.Result;
import com.group.exam.common.enums.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 全局异常处理：业务结果统一以 HTTP 200 + code 返回（API 文档 2.4）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public Result<Void> handleValid(org.springframework.web.bind.MethodArgumentNotValidException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), firstFieldError(e));
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        FieldError fe = e.getFieldError();
        String msg = fe != null ? fe.getField() + " " + fe.getDefaultMessage() : ErrorCode.PARAM_ERROR.getMessage();
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream().findFirst()
                .map(v -> v.getMessage())
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), "缺少参数: " + e.getParameterName());
    }

    /** multipart 请求缺少文件部分（如导入未选文件），返回 10001 而非 500 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<Void> handleMissingPart(MissingServletRequestPartException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), "请上传文件: " + e.getRequestPartName());
    }

    /** 参数类型转换失败（如 durationSec 传小数/非数字字符串），返回 10001 而非 500 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        if ("durationSec".equals(e.getName())) {
            return Result.error(ErrorCode.PARAM_ERROR.getCode(), "标称时长必须为非负整数秒");
        }
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), "参数格式不正确: " + e.getName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        return Result.error(ErrorCode.FILE_TOO_LARGE.getCode(), ErrorCode.FILE_TOO_LARGE.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMessage());
        return Result.error(ErrorCode.DUPLICATE_SUBMIT.getCode(), ErrorCode.DUPLICATE_SUBMIT.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统内部错误", e);
        return Result.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage() + ": " + e.getMessage());
    }

    private String firstFieldError(org.springframework.web.bind.MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        return fe != null ? fe.getField() + " " + fe.getDefaultMessage() : ErrorCode.PARAM_ERROR.getMessage();
    }
}
