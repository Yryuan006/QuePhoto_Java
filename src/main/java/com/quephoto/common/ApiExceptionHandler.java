package com.quephoto.common;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
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

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail status(
            ResponseStatusException ex,
            HttpServletResponse response
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(ex.getStatusCode());
        String title = ex.getReason() == null ? "请求失败" : ex.getReason();
        problem.setTitle(title);
        if (ex.getStatusCode().value() == 429) {
            response.setHeader("Retry-After", "60");
            problem.setProperty("errors", Map.of("login", List.of(title)));
        }
        return problem;
    }

}
