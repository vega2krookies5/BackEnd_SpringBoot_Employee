package com.employee.api.auth.config;

import com.employee.api.auth.filter.JwtAuthenticationFilter;
import com.employee.api.auth.userinfo.UserInfoUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final PasswordEncoder passwordEncoder;
    // JwtAuthenticationFilter: 요청마다 JWT 토큰을 검증하는 커스텀 필터
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                // CORS 활성화: CorsConfigurationSource 빈(prod) 또는 MVC CORS 설정(local)을 자동 참조
                // Spring Security 필터 체인 내에서 preflight(OPTIONS) 요청을 처리하므로
                // FilterRegistrationBean보다 먼저 실행되는 문제를 해결
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/employees/welcome","/userinfos/new", "/userinfos/login").permitAll()
                            .requestMatchers("/api/**").authenticated();
                })
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        // authenticationEntryPoint: 토큰 없이 인증 필요한 경로 접근 시 호출 → 401 JSON 반환
                        // 기본값은 HTML 에러 페이지이므로 REST API용 JSON 응답으로 교체
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"error\":\"Unauthorized(인증실패)\",\"message\":\"" + e.getMessage() + "\"}");
                        })
                        // accessDeniedHandler: 인증은 됐으나 권한(ROLE) 부족 시 호출 → 403 JSON 반환
                        // 필터 레벨 AccessDeniedException 처리 (컨트롤러 레벨은 DefaultExceptionAdvice 처리)
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"error\":\"Forbidden(권한없음)\",\"message\":\"" + e.getMessage() + "\"}");
                        })
                )
                // DB 기반 인증 프로바이더 등록
                .authenticationProvider(authenticationProvider())
                // JwtAuthenticationFilter를 UsernamePasswordAuthenticationFilter 앞에 삽입
                // → 폼 로그인 필터보다 먼저 JWT 토큰을 검증하여 SecurityContext에 인증 정보 설정
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * UserDetailsService 빈 등록
     * UserInfoUserDetailsService: 이메일로 DB를 조회하여 UserDetails 반환
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new UserInfoUserDetailsService();
    }

    /**
     * AuthenticationManager 빈 등록
     * UserInfoController의 로그인 처리에서 직접 주입받아 사용
     * AuthenticationConfiguration이 내부적으로 등록된 AuthenticationProvider를 조합하여 반환
     */
    @Bean
    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService());
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return authenticationProvider;
    }

    /**
     * AuthenticationManager 빈 등록
     * UserInfoController의 로그인 처리에서 직접 주입받아 사용
     * AuthenticationConfiguration이 내부적으로 등록된 AuthenticationManager를 반환
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

}