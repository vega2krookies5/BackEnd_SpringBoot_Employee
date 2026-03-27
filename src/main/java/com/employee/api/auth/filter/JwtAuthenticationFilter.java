package com.employee.api.auth.filter;

import com.employee.api.auth.jwt.JwtService;
import com.employee.api.auth.userinfo.UserInfoUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 토큰 인증 필터
 *
 * 모든 HTTP 요청에서 Authorization 헤더의 JWT 토큰을 검증하고
 * 유효한 경우 SecurityContext에 인증 정보를 등록합니다.
 *
 * OncePerRequestFilter: 요청당 한 번만 실행되도록 보장
 * (서블릿 포워딩/리다이렉트 시 중복 실행 방지)
 *
 * 처리 흐름:
 *   1. Authorization 헤더에서 "Bearer <token>" 추출
 *   2. 토큰 파싱 실패 시 → 401 JSON 반환 후 중단
 *   3. 토큰에서 username(이메일) 추출 → DB에서 UserDetails 로드
 *   4. 토큰 유효성 검증 통과 시 → SecurityContext에 인증 등록
 *   5. 다음 필터로 체인 계속
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserInfoUserDetailsService userDetailsService;

    /**
     * 요청당 한 번 실행되는 JWT 인증 처리 메서드
     *
     * @param request     HTTP 요청 (Authorization 헤더 포함)
     * @param response    HTTP 응답 (토큰 오류 시 401 직접 작성)
     * @param filterChain 다음 필터로 체인을 이어가기 위한 객체
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        // Authorization 헤더가 존재하고 "Bearer "로 시작하는 경우에만 토큰 처리
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // "Bearer " 이후 7자리부터 토큰 문자열 추출
            token = authHeader.substring(7);
            try {
                // 토큰의 sub(subject) 클레임에서 username(이메일) 추출
                username = jwtService.extractUsername(token);
            } catch (Exception e) {
                // 토큰 만료, 서명 불일치, 형식 오류 등 파싱 실패 → 401 반환 후 필터 체인 중단
                log.warn("JWT token parsing failed: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or expired JWT token\"}");
                return;
            }
        }

        // username이 추출됐고 아직 SecurityContext에 Authentication(인증) 정보가 없는 경우에만 처리
        // (이미 인증된 요청을 중복 처리하지 않기 위한 조건)
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // DB에서 사용자 정보(권한 포함) 로드 — 토큰에 roles가 없으므로 매 요청마다 DB 조회
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            log.debug("Authorities for {}: {}", username, userDetails.getAuthorities());

            // 토큰의 username과 DB의 username 일치 여부 + 만료 여부 검증
            if (jwtService.validateToken(token, userDetails)) {
                // 인증 토큰 생성: (principal, credentials=null, authorities)
                // credentials를 null로 설정 — 인증 완료 후 비밀번호 정보 불필요
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails,
                                null, userDetails.getAuthorities());
                // 요청의 IP, 세션 정보 등 부가 정보를 authToken에 추가
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // SecurityContext에 인증 정보 등록 → 이후 @PreAuthorize 등에서 사용
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        // 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }
}