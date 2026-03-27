# JWT 인증 구현 가이드

> `net.restapi.emp.security` 패키지의 모든 클래스를 중심으로
> JWT 인증/인가의 전체 흐름을 이해하기 쉽게 정리한 문서입니다.

---

## 1. JWT란 무엇인가

### 1-1. 쉬운 비유

JWT를 **호텔 카드키**에 비유할 수 있습니다.

```
일반 세션 방식 (구식 호텔 열쇠)
  - 체크인 시 프론트에서 열쇠를 받음
  - 프론트는 "304호 손님이 있다"는 정보를 서버(장부)에 기록
  - 방 출입 때마다 서버(장부)를 확인해야 함

JWT 방식 (스마트 카드키)
  - 로그인 시 서버가 "이 사람은 admin@aa.com, ROLE_ADMIN" 정보를 카드키(토큰)에 새겨서 발급
  - 이후 요청마다 카드키를 보여주면 됨
  - 서버는 카드키의 위변조 여부만 확인 → 장부(세션)가 필요 없음
```

### 1-2. JWT 구조

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJhZG1pbi4uLiIsImlhdCI6Li4ufQ . SflKxwRJSMeKKF2QT...
└──── Header ────────┘   └──────────── Payload ──────────────────┘   └── Signature ──┘
  { "alg": "HS256" }       { "sub": "email", "iat": ..., "exp": ... }   HMAC-SHA256 서명
```

| 부분 | 내용 | 역할 |
|---|---|---|
| **Header** | 알고리즘(`HS256`) | 서명 방식 명시 |
| **Payload** | `sub`(이메일), `iat`(발급시간), `exp`(만료시간) | 사용자 식별 정보 |
| **Signature** | Header + Payload를 시크릿 키로 서명 | 위변조 방지 |

> **주의**: Payload는 Base64 인코딩이므로 누구나 디코딩 가능합니다.
> 비밀번호 등 민감한 정보는 절대 포함하지 않습니다.

---

## 2. security 패키지 구조

```
net.restapi.emp.security
├── config/
│   ├── PasswordEncoderConfig      ← BCrypt 암호화 빈 등록
│   └── SecurityConfig             ← Spring Security 전체 설정 (핵심)
├── filter/
│   └── JwtAuthenticationFilter    ← 모든 요청에서 JWT 토큰 검증
├── jwt/
│   ├── AuthRequest                ← 로그인 요청 DTO (email + password)
│   └── JwtService                 ← 토큰 생성 / 파싱 / 검증 (핵심)
└── userinfo/
    ├── UserInfo                   ← 사용자 정보 JPA 엔티티 (DB 테이블)
    ├── UserInfoController         ← 회원가입 / 로그인 API
    ├── UserInfoRepository         ← DB 조회 (이메일로 사용자 찾기)
    ├── UserInfoUserDetails        ← Spring Security용 사용자 정보 어댑터
    ├── UserInfoUserDetailsService ← DB에서 사용자 로드 (Spring Security 연결고리)
    └── CurrentUser                ← 컨트롤러에서 현재 사용자 주입 어노테이션
```

---

## 3. 클래스별 상세 설명

### 3-1. PasswordEncoderConfig

**역할**: 비밀번호를 안전하게 암호화하는 BCrypt 인코더를 스프링 빈으로 등록합니다.

```java
@Configuration
public class PasswordEncoderConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**BCrypt란?**

```
평문 비밀번호: "pwd1"
BCrypt 암호화: $2a$10$mkMeA3UpwNIwdEv1v0IvruIBXemqLjxUxKhtEkWIbiot8szzs4GHC

특징:
  - 같은 "pwd1"을 암호화해도 매번 다른 해시 생성 (salt 자동 포함)
  - 단방향 암호화 → 복호화 불가
  - matches("pwd1", 해시값) 으로 일치 여부만 확인 가능
```

> `@Configuration`이 분리된 이유: `SecurityConfig`에서 `PasswordEncoder`와
> `UserDetailsService`를 동시에 빈으로 등록하면 순환참조가 발생하기 때문입니다.

---

### 3-2. UserInfo (엔티티)

**역할**: `user_info` DB 테이블과 매핑되는 JPA 엔티티입니다. 사용자의 실제 데이터를 담습니다.

```java
@Entity
public class UserInfo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;        // 로그인 ID로 사용

    private String password;     // BCrypt 암호화된 비밀번호

    private String roles;        // "ROLE_ADMIN" 또는 "ROLE_ADMIN,ROLE_USER"
}
```

```
DB user_info 테이블
┌────┬───────────┬──────────────┬──────────────────────┬──────────────────────┐
│ id │   name    │    email     │       password       │        roles         │
├────┼───────────┼──────────────┼──────────────────────┼──────────────────────┤
│  1 │ adminboot │ admin@aa.com │ $2a$10$mkMeA3Up...   │ ROLE_ADMIN,ROLE_USER │
│  2 │ user1     │ user@aa.com  │ $2a$10$xyz...        │ ROLE_USER            │
└────┴───────────┴──────────────┴──────────────────────┴──────────────────────┘
```

---

### 3-3. AuthRequest (로그인 요청 DTO)

**역할**: `POST /userinfos/login` 요청 바디를 받는 DTO입니다.

```java
@Data
public class AuthRequest {
    @Email
    private String email;     // "admin@aa.com"

    @Size(min = 4)
    private String password;  // "pwd1"
}
```

---

### 3-4. UserInfoUserDetails (Spring Security 어댑터)

**역할**: `UserInfo`(우리가 만든 엔티티)를 Spring Security가 이해할 수 있는
`UserDetails` 인터페이스로 변환하는 어댑터 클래스입니다.

```
UserInfo (우리 엔티티)         UserDetails (Spring Security 인터페이스)
┌──────────────────┐           ┌──────────────────────────┐
│ email            │           │ getUsername() → email    │
│ password         │  변환     │ getPassword() → password │
│ roles (문자열)   │ ────────> │ getAuthorities() → List  │
│                  │           │ isAccountNonExpired()    │
│                  │           │ isAccountNonLocked()     │
│                  │           │ isEnabled()              │
└──────────────────┘           └──────────────────────────┘
```

**roles 파싱 방법:**

```java
// DB의 roles 문자열을 GrantedAuthority 목록으로 변환
// "ROLE_ADMIN,ROLE_USER" → [ROLE_ADMIN, ROLE_USER]
this.authorities = Arrays.stream(userInfo.getRoles().split(","))
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());
```

> `@PreAuthorize("hasAuthority('ROLE_ADMIN')")` 은 이 `authorities` 목록을 확인합니다.

---

### 3-5. UserInfoUserDetailsService (사용자 로드 서비스)

**역할**: Spring Security가 인증이 필요할 때 "이 이메일을 가진 사용자를 DB에서 찾아줘"라고
호출하는 서비스입니다. `UserDetailsService` 인터페이스를 구현합니다.

```java
@Service
public class UserInfoUserDetailsService implements UserDetailsService {

    // Spring Security가 인증 시 자동 호출
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // username = 이메일 (JWT 토큰의 sub 클레임)
        Optional<UserInfo> optionalUserInfo = repository.findByEmail(username);
        return optionalUserInfo
                .map(UserInfoUserDetails::new)     // UserInfo → UserInfoUserDetails 변환
                .orElseThrow(() -> new UsernameNotFoundException("user not found " + username));
    }

    // 회원가입 시 호출 — 비밀번호 BCrypt 암호화 후 저장
    public String addUser(UserInfo userInfo) {
        userInfo.setPassword(passwordEncoder.encode(userInfo.getPassword()));
        repository.save(userInfo);
        return userInfo.getName() + " user added!!";
    }
}
```

**호출 시점:**

```
① 로그인 시: AuthenticationManager가 비밀번호 검증을 위해 호출
② API 요청 시: JwtAuthenticationFilter가 토큰에서 이메일 추출 후 DB에서 사용자 정보(권한 포함) 로드
```

---

### 3-6. JwtService (JWT 핵심 서비스)

**역할**: JWT 토큰의 **생성 → 파싱 → 검증** 3가지 핵심 기능을 담당합니다.

#### 설정값 (application.properties에서 주입)

```properties
jwt.secret=5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437
jwt.expiration=3600
```

```java
@Value("${jwt.secret}")
private String secret;      // 서명에 사용할 비밀 키 (Base64 인코딩된 문자열)

@Value("${jwt.expiration}")
private int expiration;     // 토큰 유효 시간 (초, 3600 = 60분)
```

#### 기능 1. 토큰 생성 — `generateToken()`

로그인 성공 후 이메일을 받아 JWT 토큰을 만들어 반환합니다.

```
입력: "admin@aa.com"
        │
        ▼
Jwts.builder()
  .subject("admin@aa.com")        → Payload의 sub 클레임
  .issuedAt(지금시간)              → Payload의 iat 클레임
  .expiration(지금+3600초)         → Payload의 exp 클레임
  .signWith(시크릿키, HS256)       → Signature 생성
  .compact()
        │
        ▼
출력: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbi4u..."
```

> **주의**: `roles`는 토큰에 포함하지 않습니다.
> 매 요청마다 DB에서 최신 권한을 조회하므로 권한이 변경되어도 즉시 반영됩니다.

#### 기능 2. 토큰 파싱 — `extractUsername()`

요청에서 받은 토큰을 분해해 사용자 이메일을 꺼냅니다.

```
입력: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbi4u..."
        │
        ▼
Jwts.parser()
  .verifyWith(시크릿키)    → 서명 검증 (위변조 확인)
  .build()
  .parseSignedClaims(token)
  .getPayload()
  .getSubject()
        │
        ▼
출력: "admin@aa.com"
```

서명이 맞지 않으면 예외 발생 → `JwtAuthenticationFilter`의 `catch`에서 401 반환

#### 기능 3. 토큰 검증 — `validateToken(token, userDetails)`

파싱된 이메일과 DB의 사용자가 일치하는지, 만료되지 않았는지 최종 확인합니다.

```
검증 조건 1: 토큰의 이메일 == DB에서 로드한 사용자의 이메일
검증 조건 2: 토큰 만료 시간 > 현재 시간

두 조건 모두 true → SecurityContext에 인증 정보 등록
```

#### 서명 키 생성 — `getSigningKey()` (private)

```java
// BASE64로 인코딩된 시크릿 문자열 → 실제 바이너리 키(32byte = 256bit)로 디코딩
private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
}
```

| 방식 | 문제 |
|---|---|
| `SECRET.getBytes()` (구버전) | 문자열을 ASCII 바이트로 변환 → 의도한 키 값이 아님 |
| `Decoders.BASE64.decode()` (현재) | Base64 디코딩으로 실제 바이너리 키 사용 → 올바른 방식 |

---

### 3-7. JwtAuthenticationFilter (요청 필터)

**역할**: 모든 API 요청이 컨트롤러에 도달하기 전에 JWT 토큰을 검증하고,
유효하면 Spring Security의 인증 컨텍스트에 사용자 정보를 등록합니다.

`OncePerRequestFilter`를 상속 → 요청당 **딱 한 번**만 실행됩니다.

#### 처리 흐름

```
API 요청 수신
    │
    ▼
Authorization 헤더 확인
    ├─ 헤더 없음 또는 "Bearer "로 시작 안 함
    │       → token/username = null
    │       → SecurityContext 등록 없이 다음 필터로
    │
    └─ "Bearer eyJhbG..." 형태
            │
            ▼
        토큰에서 이메일 추출 시도
            ├─ 실패 (만료/위변조/형식오류)
            │       → 401 JSON 즉시 반환, 필터 체인 중단
            │
            └─ 성공 → username = "admin@aa.com"
                    │
                    ▼
                DB에서 UserDetails 로드
                (roles 포함, 매 요청마다 최신 정보 조회)
                    │
                    ▼
                validateToken() 최종 검증
                    ├─ 실패 → SecurityContext 등록 안 함
                    │
                    └─ 성공
                            │
                            ▼
                        SecurityContextHolder에 인증 정보 등록
                        (principal=UserDetails, authorities=roles)
                            │
                            ▼
                        다음 필터 → 컨트롤러 → @PreAuthorize 검사
```

---

### 3-8. SecurityConfig (보안 설정의 중심)

**역할**: Spring Security의 모든 보안 규칙을 한 곳에서 설정하는 클래스입니다.
"어떤 요청을 허용/차단할지", "어떻게 인증할지", "에러 응답은 어떻게 할지"를 정의합니다.

#### 어노테이션 의미

```java
@Configuration          // 설정 클래스
@EnableWebSecurity      // Spring Security 활성화
@EnableMethodSecurity   // @PreAuthorize 어노테이션 활성화
@RequiredArgsConstructor // 생성자 주입
```

#### securityFilterChain() — 핵심 메서드

HTTP 요청에 대한 보안 규칙을 체인 방식으로 설정합니다.

```java
return http
    .csrf(csrf -> csrf.disable())
    // ① CSRF 비활성화
    //    - CSRF는 브라우저 쿠키/세션 기반 공격 방어
    //    - JWT는 쿠키/세션을 사용하지 않으므로 불필요

    .cors(Customizer.withDefaults())
    // ② CORS 활성화
    //    - React(3000) → Spring Boot(8080) 크로스 도메인 요청 허용
    //    - CorsConfigurationSource 빈(CorsConfig)을 자동 참조

    .authorizeHttpRequests(auth -> {
        auth.requestMatchers(
            "/api/employees/welcome",
            "/userinfos/new",
            "/userinfos/login"
        ).permitAll()
        // ③ 공개 경로: 토큰 없이 접근 가능
        //    - 로그인, 회원가입은 당연히 토큰이 없어야 함

        .requestMatchers("/api/**").authenticated();
        // ④ 보호 경로: 토큰 있어야 접근 가능
        //    - /api/employees, /api/departments 등
        //    - 추가 권한(ROLE_ADMIN 등)은 @PreAuthorize에서 검사
    })

    .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    // ⑤ 세션 정책: STATELESS
    //    - 서버에 세션을 저장하지 않음
    //    - JWT 토큰만으로 인증 → 수평 확장에 유리

    .exceptionHandling(ex -> ex
        .authenticationEntryPoint(...)    // ⑥ 401 처리
        .accessDeniedHandler(...)         // ⑦ 403 처리
    )

    .authenticationProvider(authenticationProvider())
    // ⑧ DaoAuthenticationProvider 등록
    //    - 로그인 시 DB에서 사용자 조회 + 비밀번호 검증

    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
    // ⑨ JWT 필터 삽입
    //    - Spring의 기본 폼 로그인 필터보다 먼저 실행
    //    - 토큰이 있으면 SecurityContext에 인증 정보를 미리 등록

    .build();
```

#### 빈 등록 메서드들

```
userDetailsService()      DB에서 사용자를 찾는 서비스 등록
authenticationProvider()  로그인 인증 방식 정의 (DB조회 + BCrypt 검증)
authenticationManager()   로그인 처리에서 직접 사용 (UserInfoController에서 주입받음)
```

#### 필터 실행 순서 (중요)

```
요청 도착
    │
    ▼ order: -100 (가장 먼저)
Spring Security FilterChain
    ├─ CORS 처리 (.cors)
    ├─ JwtAuthenticationFilter (토큰 검증, SecurityContext 등록)
    └─ 인증/인가 검사 (authorizeHttpRequests)
    │
    ▼ order: 0 (나중)
일반 서블릿 필터들
    │
    ▼
DispatcherServlet → Controller → @PreAuthorize
```

#### 에러 처리 (exceptionHandling)

| 상황 | 처리 | 응답 |
|---|---|---|
| 토큰 없이 보호 경로 접근 | `authenticationEntryPoint` | 401 JSON |
| 권한 부족 (필터 레벨) | `accessDeniedHandler` | 403 JSON |
| `@PreAuthorize` 실패 | `DefaultExceptionAdvice` | 403 JSON |

---

### 3-9. CurrentUser (커스텀 어노테이션)

**역할**: 컨트롤러 메서드 파라미터에서 현재 로그인한 사용자 정보를 편리하게 주입받습니다.

```java
@AuthenticationPrincipal(expression = "#this == 'anonymousUser' ? null : userInfo")
public @interface CurrentUser {}
```

**사용 예시:**

```java
@GetMapping
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public ResponseEntity<List<EmployeeDto>> getAllEmployees(@CurrentUser UserInfo currentUser) {
    log.info("요청자: {} [{}]", currentUser.getName(), currentUser.getEmail());
    // @PreAuthorize를 통과한 경우 currentUser는 항상 non-null
    return ResponseEntity.ok(employeeService.getAllEmployees());
}
```

```
SecurityContext에 등록된 인증 객체
    └─ principal = UserInfoUserDetails
            └─ getUserInfo() → UserInfo  ← @CurrentUser가 이 값을 주입
```

---

## 4. 전체 인증 흐름

### 4-1. 로그인 → 토큰 발급

```
[클라이언트]
POST /userinfos/login
{ "email": "admin@aa.com", "password": "pwd1" }
    │
    ▼
[JwtAuthenticationFilter]
    └─ Authorization 헤더 없음 → 건너뜀
    │
    ▼
[UserInfoController.authenticateAndGetToken()]
    │
    ├─ authenticationManager.authenticate()
    │       │
    │       └─ DaoAuthenticationProvider
    │               ├─ UserInfoUserDetailsService.loadUserByUsername("admin@aa.com")
    │               │       └─ DB에서 UserInfo 조회 → UserInfoUserDetails 반환
    │               └─ BCryptPasswordEncoder.matches("pwd1", "$2a$10$mk...")
    │                       └─ 일치 → 인증 성공
    │
    └─ JwtService.generateToken("admin@aa.com")
            └─ JWT 토큰 생성 (sub=email, exp=현재+3600초)
    │
    ▼
[클라이언트] 토큰 수신 → localStorage 저장
```

### 4-2. API 요청 → 인가

```
[클라이언트]
GET /api/employees
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
    │
    ▼
[JwtAuthenticationFilter]
    ├─ 헤더에서 토큰 추출
    ├─ JwtService.extractUsername(token) → "admin@aa.com"
    ├─ UserInfoUserDetailsService.loadUserByUsername("admin@aa.com")
    │       └─ DB 조회 → UserInfoUserDetails (권한: ROLE_ADMIN, ROLE_USER 포함)
    ├─ JwtService.validateToken(token, userDetails) → true
    └─ SecurityContextHolder에 인증 정보 등록
    │
    ▼
[EmployeeController.getAllEmployees()]
    ├─ @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    │       └─ SecurityContext에서 권한 확인 → ROLE_ADMIN 있음 → 통과
    └─ 직원 목록 반환
```

---

## 5. 코드 개선 내용

### 5-1. 개선 전/후 비교

| 항목 | 개선 전 | 개선 후 |
|---|---|---|
| 시크릿 키 위치 | 코드에 `public static final` 하드코딩 | `application.properties` + `@Value` 주입 |
| 키 접근 제어 | `public static` → 외부 직접 접근 가능 | `private getSigningKey()` 로 캡슐화 |
| 키 생성 방식 | `SECRET.getBytes()` (잘못된 방식) | `Decoders.BASE64.decode()` (올바른 방식) |
| 만료 시간 위치 | 코드에 하드코딩 | `application.properties` + `@Value` 주입 |
| 의존성 주입 방식 | `@Autowired` 필드 주입 | `@RequiredArgsConstructor` 생성자 주입 |
| 토큰 파싱 예외 처리 | 예외 전파 → 500 | try-catch → 401 JSON 반환 |
| 권한 로그 레벨 | `log.info` (매 요청 출력) | `log.debug` (필요 시만 확인) |
| 세션 정책 | 미설정 | `SessionCreationPolicy.STATELESS` |
| 반환 타입 | `Boolean` (래퍼) | `boolean` (프리미티브) |

### 5-2. 시크릿 키 외부화

```properties
# application.properties
jwt.secret=${JWT_SECRET:5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437}
jwt.expiration=${JWT_EXPIRATION:3600}
```

- `${JWT_SECRET:기본값}` — 환경변수가 있으면 환경변수를, 없으면 기본값 사용
- 운영 환경에서는 반드시 환경변수로 주입 (Git에 시크릿 키 노출 방지)

---

## 6. API 사용법

### 6-1. 회원 가입

```
POST http://localhost:8080/userinfos/new
Content-Type: application/json

{
  "name": "홍길동",
  "email": "admin@aa.com",
  "password": "pwd1",
  "roles": "ROLE_ADMIN"
}
```

> `roles` 값: `ROLE_USER`, `ROLE_ADMIN`, 복수 권한은 `"ROLE_ADMIN,ROLE_USER"` (공백 없음)

### 6-2. 로그인 및 토큰 발급

```
POST http://localhost:8080/userinfos/login
Content-Type: application/json

{
  "email": "admin@aa.com",
  "password": "pwd1"
}
```

**응답** (plain text):
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbi5...
```

### 6-3. 토큰으로 API 호출

```
GET http://localhost:8080/api/employees
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbi5...
```

### 6-4. 토큰 디코딩 확인 (jwt.io)

```json
// Header
{ "alg": "HS256" }

// Payload
{
  "sub": "admin@aa.com",
  "iat": 1742745600,
  "exp": 1742749200
}
```

---

## 7. 에러 처리 구조

### 7-1. 에러 응답 형식

**표준 에러 (DefaultExceptionAdvice)**
```json
{ "statusCode": 401, "message": "...", "timestamp": "2026-03-26 10:00:00 목 오전" }
```

**필터 레벨 에러 (JwtAuthenticationFilter / SecurityConfig)**
```json
{ "error": "Unauthorized", "message": "..." }
```

### 7-2. 401 / 403 처리 경로

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 상황                          처리 위치                      상태
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 만료/위변조 토큰               JwtAuthenticationFilter        401
 토큰 없이 보호 경로 접근       SecurityConfig entryPoint      401
 로그인 자격증명 오류           DefaultExceptionAdvice         401
 @PreAuthorize 권한 부족        DefaultExceptionAdvice         403
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 7-3. DefaultExceptionAdvice 핸들러 우선순위

| 순위 | 예외 타입 | 상태 | 발생 상황 |
|---|---|---|---|
| 1 | `ResourceNotFoundException` | 417/404 | 리소스 없음 |
| 2 | `HttpMessageNotReadableException` | 400 | 잘못된 요청 바디 |
| 3 | `MethodArgumentNotValidException` | 400 | 입력값 검증 실패 |
| 4 | `AuthenticationException` | **401** | 인증 실패 |
| 5 | `AccessDeniedException` | **403** | 권한 부족 |
| 6 | `RuntimeException` | 500 | 그 외 예외 |

> `@ExceptionHandler`는 **가장 구체적인 타입**을 우선 매칭합니다.
> `AccessDeniedException` 전용 핸들러가 없으면 `RuntimeException` 핸들러가 처리하여 500이 반환됩니다.

---

## 8. CORS 설정

### 8-1. 왜 필요한가

```
클라이언트: http://localhost:3000  (React)
백엔드    : http://localhost:8080  (Spring Boot)
→ 포트가 다름 = 다른 출처(Cross-Origin) → 브라우저가 기본적으로 차단
```

### 8-2. Preflight 요청 문제

브라우저는 `Authorization` 헤더가 포함된 요청 전에 OPTIONS 메서드로 사전 요청을 보냅니다.

```
① OPTIONS /api/employees  (preflight — 브라우저 자동 발송)
② 서버: Access-Control-Allow-Origin 헤더 응답 없음 → CORS 오류
③ 실제 GET 요청 차단됨
```

### 8-3. 기존 문제 원인

```
FilterRegistrationBean CorsFilter (order: 0)
    → Spring Security FilterChain (order: -100) 보다 늦게 실행
    → preflight가 Security에서 먼저 차단됨
```

### 8-4. 해결 방법

```java
// SecurityConfig에 추가
.cors(Customizer.withDefaults())
// → CorsConfigurationSource 빈(prod 프로파일)을 Spring Security 내부에서 직접 참조
// → preflight 요청을 Security 필터 체인 내에서 처리
```

### 8-5. 프로파일별 CORS 설정

| 프로파일 | 클래스 | 방식 |
|---|---|---|
| `prod` | `CorsConfig` | `CorsConfigurationSource` 빈 등록 |
| `local` | `WebConfig` | `WebMvcConfigurer.addCorsMappings()` |

**핵심 허용 설정:**
```java
configuration.setAllowedHeaders(Arrays.asList(
    "Origin", "Content-Type", "Accept",
    "Authorization"    // JWT Bearer 토큰 전송에 반드시 필요
));
configuration.setAllowedMethods(Arrays.asList(
    "GET", "POST", "PUT", "DELETE", "PATCH",
    "OPTIONS"          // preflight 요청 처리에 반드시 필요
));
```

---

## 9. 주의사항

| 항목 | 내용 |
|---|---|
| 시크릿 키 길이 | HS256 최소 256bit (32byte). `BASE64.decode()` 결과가 32byte 이상이어야 함 |
| 시크릿 키 노출 | 절대 Git에 커밋 금지. 운영 환경에서는 `JWT_SECRET` 환경변수로 주입 |
| roles 형식 | 반드시 `ROLE_USER`, `ROLE_ADMIN` 형식. 복수는 `"ROLE_ADMIN,ROLE_USER"` (공백 없음) |
| 토큰 저장 | React: `localStorage` 또는 `sessionStorage`에 저장 |
| Authorization 헤더 | API 요청 시 `Authorization: Bearer <token>` 형식 |
| 토큰 만료 | 기본 3600초(60분). 만료 후 재로그인 필요 (`/userinfos/login`) |
| roles 미포함 | 토큰 Payload에 roles 없음 → 매 요청마다 DB에서 최신 권한 조회 |
| Stateless | `SessionCreationPolicy.STATELESS` → 서버 세션 미생성, 수평 확장에 유리 |
