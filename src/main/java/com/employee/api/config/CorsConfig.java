package com.employee.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 설정 (prod 프로파일)
 *
 * CorsConfigurationSource 빈을 등록하면 SecurityConfig의
 * .cors(Customizer.withDefaults())가 이 빈을 자동으로 참조합니다.
 *
 * 기존 FilterRegistrationBean 방식의 문제:
 *   - FilterRegistrationBean order(0)은 Spring Security(-100)보다 늦게 실행
 *   - preflight(OPTIONS) 요청이 Security 필터에서 먼저 차단됨
 * 해결:
 *   - CorsConfigurationSource 빈 등록 → Security 필터 체인 내부에서 CORS 처리
 */
@Configuration
@Profile("prod")
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 모든 출처 허용 (운영 환경에서는 실제 클라이언트 도메인으로 제한 권장)
        configuration.setAllowedOriginPatterns(List.of("*"));

        // 자격증명(쿠키, Authorization 헤더) 포함 요청 허용
        configuration.setAllowCredentials(true);

        // 허용할 요청 헤더 — Authorization(JWT 토큰) 반드시 포함
        configuration.setAllowedHeaders(Arrays.asList(
                "Origin", "Content-Type", "Accept",
                "Authorization",
                "Access-Control-Allow-Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        // 허용할 HTTP 메서드 — OPTIONS는 preflight 요청 처리에 필수
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // preflight 응답 캐시 시간 (초) — 동일 경로의 반복 preflight 요청 감소
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
