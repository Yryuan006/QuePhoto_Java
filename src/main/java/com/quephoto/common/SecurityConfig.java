package com.quephoto.common;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JsonMapper json) throws Exception {
        // 同一组匹配规则同时用于权限判断和错误响应分类。
        var paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher publicApi = new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, "/api/auth/login"),
                paths.matcher(HttpMethod.GET, "/api/portfolios"),
                paths.matcher(HttpMethod.GET, "/api/portfolios/*"),
                paths.matcher(HttpMethod.GET, "/api/tag-groups")
        );
        RequestMatcher adminApi = paths.matcher("/api/admin/**");
        RequestMatcher knownApi = new OrRequestMatcher(publicApi, adminApi);

        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            if (!knownApi.matches(request)) {
                writeProblem(json, response, 404, "接口不存在");
                return;
            }
            response.setHeader("WWW-Authenticate", "Bearer");
            writeProblem(json, response, 401, "请登录或重新登录");
        };

        AccessDeniedHandler forbidden = (request, response, exception) -> {
            if (!knownApi.matches(request)) {
                writeProblem(json, response, 404, "接口不存在");
                return;
            }
            writeProblem(json, response, 403, "没有访问权限");
        };

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(publicApi).permitAll()
                        .requestMatchers(adminApi).hasAuthority("SCOPE_admin")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden));

        return http.build();
    }

    private static void writeProblem(
            JsonMapper json, HttpServletResponse response, int status, String title
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), Map.of(
                "title", title,
                "status", status,
                "errors", Map.of("security", List.of(title))
        ));
    }
}
