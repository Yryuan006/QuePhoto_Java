package com.quephoto.common;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** 将请求处理中的指定异常统一转换成错误响应，由 Spring 自动调用。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 保留异常的 HTTP 状态码和说明，例如 404“作品不存在”。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail status(ResponseStatusException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(ex.getStatusCode());
        problem.setTitle(ex.getReason() == null ? "请求失败" : ex.getReason());
        return problem;
    }

    /** 参数超出范围或类型不匹配时，返回 400 和统一的参数错误提示。 */
    @ExceptionHandler({
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ProblemDetail badQuery(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("请求校验失败");
        // 自定义字段会出现在响应 JSON 的 errors.query 中。
        problem.setProperty("errors", Map.of(
            "query", List.of("请检查分页参数或 ID 的范围与类型。")
        ));
        return problem;
    }
}
