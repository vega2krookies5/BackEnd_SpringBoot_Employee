package com.employee.api.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecureDigestAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

/**
 * JWT(JSON Web Token) 생성 및 검증 서비스
 *
 * 주요 기능:
 *   - 토큰 생성 (generateToken)
 *   - 토큰에서 클레임 추출 (extractUsername, extractExpiration)
 *   - 토큰 유효성 검증 (validateToken)
 *
 * 사용 라이브러리: jjwt 0.12.x (io.jsonwebtoken)
 * 서명 알고리즘: HMAC-SHA256 (HS256)
 *
 * 토큰 구조:
 *   Header  : { "alg": "HS256" }
 *   Payload : { "sub": email, "iat": 발급시간, "exp": 만료시간 }
 *   Signature: HMAC-SHA256(Base64(Header) + "." + Base64(Payload), 시크릿키)
 */
@Component
public class JwtService {

    // 시크릿 키: application.properties의 jwt.secret 값 주입
    // 운영 환경에서는 환경변수 JWT_SECRET으로 외부 주입 권장 (코드/설정 파일 노출 방지)
    @Value("${jwt.secret}")
    private String secret;

    // 토큰 만료 시간(초): application.properties의 jwt.expiration 값 주입 (기본 3600초 = 60분)
    @Value("${jwt.expiration}")
    private int expiration;

    // jjwt 0.12.x 방식: SignatureAlgorithm enum 대신 Jwts.SIG.HS256 사용
    private static final SecureDigestAlgorithm<SecretKey, SecretKey> ALGORITHM = Jwts.SIG.HS256;

    /**
     * 서명 키 생성 (private 캡슐화)
     * Base64로 인코딩된 시크릿 문자열을 디코딩하여 HMAC-SHA256용 SecretKey 반환
     * public static 필드 대신 private 메서드로 캡슐화하여 외부 접근 차단
     */
    private SecretKey getSigningKey() {
        // Decoders.BASE64.decode(): Base64 문자열 → 실제 바이너리 키 바이트(32byte = 256bit)
        // SECRET.getBytes() 방식은 의도한 키 값이 아닐 수 있어 사용 금지
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    /**
     * 토큰의 모든 클레임(Payload) 파싱
     * 서명 검증 실패 또는 만료된 토큰이면 jjwt가 예외를 던짐
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())  // 서명 검증에 사용할 키 지정
                .build()
                .parseSignedClaims(token)     // 토큰 파싱 및 서명 검증
                .getPayload();                // Claims(Payload) 반환
    }

    /**
     * 클레임에서 원하는 값을 추출하는 범용 메서드
     * Function<Claims, T>를 통해 원하는 클레임 필드를 유연하게 추출
     *
     * @param token          JWT 토큰 문자열
     * @param claimsResolver 추출할 클레임을 지정하는 함수 (예: Claims::getSubject)
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 토큰에서 username(이메일) 추출
     * sub(subject) 클레임에 저장된 이메일 반환
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 토큰에서 만료 시간 추출
     * exp(expiration) 클레임 반환
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * 토큰 만료 여부 확인
     * 만료 시간이 현재 시간보다 이전이면 true (만료됨)
     * Boolean(래퍼 클래스) 대신 boolean(프리미티브) 사용 — 불필요한 오토박싱 제거
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * 토큰 유효성 검증 (UserDetails 비교)
     * JwtAuthenticationFilter에서 인증 등록 전에 호출
     *
     * 검증 조건:
     *   1. 토큰의 subject(이메일)와 UserDetails의 username 일치 여부
     *   2. 토큰 만료 여부
     *
     * @param token       검증할 JWT 토큰
     * @param userDetails DB에서 로드한 사용자 정보
     * @return 두 조건 모두 통과 시 true
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    /**
     * 토큰 유효성 검증 (서명/만료만 확인, UserDetails 비교 없음)
     * 파싱 성공 시 true, 실패 시 AuthenticationCredentialsNotFoundException 발생
     *
     * @param token 검증할 JWT 토큰
     * @throws AuthenticationCredentialsNotFoundException 토큰 만료 또는 서명 불일치 시
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            throw new AuthenticationCredentialsNotFoundException("JWT was expired or incorrect", ex);
        }
    }

    /**
     * JWT 토큰 생성
     * 로그인 성공 후 UserInfoController에서 호출
     *
     * 토큰 Payload 구성:
     *   - subject : 사용자 이메일 (username)
     *   - issuedAt: 현재 시간 (iat)
     *   - expiration: 현재 시간 + expiration초 (exp)
     *
     * 주의: roles 정보는 Payload에 포함하지 않음
     *       → 매 요청마다 DB에서 권한을 재조회하므로 항상 최신 권한 적용 가능
     *
     * @param userName 토큰 subject에 저장할 사용자 이메일
     * @return 서명된 JWT 토큰 문자열
     */
    public String generateToken(String userName) {
        Date expireDate = Date.from(Instant.now().plusSeconds(expiration));

        return Jwts.builder()
                .signWith(getSigningKey(), ALGORITHM) // 서명 알고리즘: HS256
                .subject(userName)                    // sub 클레임: 사용자 이메일
                .issuedAt(new Date())                 // iat 클레임: 발급 시간
                .expiration(expireDate)               // exp 클레임: 만료 시간
                .compact();                           // 최종 JWT 문자열 생성
    }
}