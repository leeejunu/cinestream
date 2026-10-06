# api 아키텍처

헥사고날 아키텍처(Ports and Adapters)를 계층 우선 패키지로 구성한다. 비즈니스 로직을 가운데 두고,
DB·메시지 큐·스토리지 같은 바깥 시스템은 `infrastructure`의 어댑터로 붙인다.

## 의존성 규칙

```
presentation ──▶ application ──▶ domain
infrastructure ─┘      ▲
                       └── global (error, config, security)
```

- 의존은 바깥에서 안쪽으로만 향한다. domain은 바깥 계층을 모르고, application은 infrastructure·presentation을 모른다.
- application은 DB에 직접 접근하지 않고 `domain/repository` 인터페이스를 호출한다. 구현은 `infrastructure/persistence`에 있다.
- presentation은 유스케이스만 호출한다. 리포지토리나 infrastructure를 직접 쓰지 않는다.
- 규칙은 `LayerDependencyTest`(ArchUnit)가 검사한다.

| 계층 | 의존 가능 | 의존 금지 |
|---|---|---|
| domain | JDK, Lombok, JPA 애너테이션, `global/error` | Spring, 다른 계층 |
| application | domain, global, Spring(`@Service`, `@Transactional`, `PasswordEncoder`) | infrastructure, presentation |
| infrastructure | domain, application, global | presentation |
| presentation | application, domain(model·error), global, Spring Web·Security | infrastructure, `domain/repository` |

## 패키지 구조

```
com.threem.api
├── domain/
│   ├── model/                # 엔티티(JPA)와 enum. User, RefreshToken, Role, AuthProvider
│   ├── repository/           # 저장소 포트. UserRepository, RefreshTokenRepository
│   └── error/                # 도메인 에러 코드. UserErrorCode
├── application/
│   ├── usecase/              # 유스케이스 인터페이스. AuthUseCase, UserUseCase
│   ├── service/              # 유스케이스 구현. AuthService, UserService
│   └── dto/                  # Command(입력), Result(출력) record
├── infrastructure/           # 바깥 시스템 연동
│   ├── persistence/{대상}/   # XxxJpaRepository + XxxRepositoryAdapter
│   ├── messaging/            # (예정) transcoder 요청 발행, 결과 수신
│   └── storage/              # (예정) S3 스토리지 (로컬은 MinIO)
├── presentation/
│   ├── controller/           # REST 컨트롤러
│   ├── dto/req, dto/res      # 요청·응답 DTO
│   └── GlobalExceptionHandler
└── global/
    ├── config/               # Clock, AuthProperties
    ├── error/                # ErrorCode, ErrorType, BusinessException (순수 자바)
    └── security/             # SecurityConfig, JwtProvider, OAuth2LoginSuccessHandler, ...
```

## 계층별 규칙

### domain

- `model`의 클래스는 JPA 엔티티를 겸한다. JPA 애너테이션(`@Entity`, `@ManyToOne` 등)은 쓰고, Spring 빈·Web·Jackson 애너테이션은 쓰지 않는다.
- `@Setter`를 쓰지 않는다. 상태 변경과 불변식 검사는 도메인 메서드에서 한다
  (예: `user.recordLoginFailure(now)`, `refreshToken.use(now)`). 허용되지 않는 변경은 `BusinessException`을 던진다.
- 생성은 정적 팩토리 메서드로 한다 (`User.signUp(...)`). JPA용 기본 생성자는 `protected`.
- 현재 시각이 필요하면 `Instant now`를 인자로 받는다. 도메인이 `Clock`을 직접 쓰지 않는다.
- 연관관계는 `@ManyToOne(fetch = LAZY)` 단방향을 기본으로 한다. `@OneToMany`는 필요할 때만 둔다.
- `repository`의 인터페이스는 도메인 언어로 짓는다 (`findByProvider`, `revokeAllByUserId`).

### application

- **usecase**: 대상별로 `{대상}UseCase` 인터페이스 하나를 두고 관련 메서드를 모은다
  (예: `AuthUseCase.login`, `UserUseCase.getMyInfo`). 메서드 하나짜리 인터페이스로 쪼개지 않는다.
  입력은 `dto`의 Command record이고, 입력 형식 검증은 record의 compact constructor에서 한다.
- **service**: `{대상}UseCase`를 구현하는 `{대상}Service`. 트랜잭션 경계는 여기에 `@Transactional`로 잡는다.
  실패해도 저장해야 하는 변경(로그인 실패 횟수, 토큰 폐기)이 있으면 `noRollbackFor = BusinessException.class`를 쓴다.
- `Clock`을 주입받아 `Instant.now(clock)` 값을 도메인에 넘긴다.
- 반환은 도메인 객체나 `dto`의 Result record로 한다. 웹 응답 DTO는 만들지 않는다.

### infrastructure

- **persistence**: `domain/repository`를 구현하는 `XxxRepositoryAdapter`와 Spring Data `XxxJpaRepository`.
  JPA 리포지토리는 패키지 밖으로 내보내지 않는다(package-private).
  목록 조회는 fetch join이나 DTO projection으로 N+1을 피한다.
- **messaging**(예정): `RabbitTemplate`으로 발행하고 `@RabbitListener`로 받는다. 메시지 DTO는 이 패키지에 둔다.
  DB 변경과 발행이 같이 일어나면 트랜잭션 커밋 후에 발행한다. 같은 결과 메시지가 두 번 와도 안전해야 한다.
- **storage**(예정): AWS SDK S3 구현 하나만 둔다. 로컬은 MinIO(S3 호환), 운영은 S3를 쓰고
  엔드포인트·자격 증명 같은 설정값만 프로필별로 다르다.

### presentation

- 요청 DTO(record + Bean Validation) → Command 변환 → 유스케이스 호출 → 응답 DTO 변환. 비즈니스 판단은 하지 않는다.
- `GlobalExceptionHandler`만 `ErrorType`을 HTTP 상태로 바꾼다. 응답 형식은 `{ code, message }`.

### global

- `error`: `ErrorCode`는 코드, 메시지, `ErrorType`(INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT,
  TOO_MANY_REQUESTS, INTERNAL)을 갖는다. 공통 코드는 `CommonErrorCode`, 도메인별 코드는 `domain/error`에 enum으로 둔다.
- `security`: 아래 "인증" 참고.

## 인증

| 항목 | 방식 |
|---|---|
| Access Token | JWT(HS256), 30분. `sub`=회원 ID, `role` 클레임. `JwtProvider`가 발급하고 Spring Security resource server가 검증 |
| Refresh Token | 난수 문자열, 14일. DB(`refresh_tokens`)에 SHA-256 해시만 저장. `HttpOnly; SameSite=Lax; Path=/api/v1/auth` 쿠키 |
| 재발급 | 쓸 때마다 새 토큰으로 바꾼다(rotation). 이미 쓴 토큰이 다시 오면 그 회원의 Refresh Token을 모두 폐기 |
| Google 로그인 | Spring Security `oauth2Login`. 성공하면 Refresh Token 쿠키를 심고 `{frontendUrl}/oauth/callback`으로 리다이렉트. 프론트엔드는 `POST /api/v1/auth/refresh`로 Access Token을 받는다 |
| 회원 식별 | 일반·Google 회원 모두 `users` 테이블. `provider`(LOCAL, GOOGLE)와 `provider_id`로 구분 |

설정은 `threem.auth.*`(`AuthProperties`)와 `spring.security.oauth2.client.registration.google.*`.
운영 환경 변수: `JWT_SECRET`, `FRONTEND_URL`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`.

## 메시지 계약 (transcoder)

| 방향 | 큐 | 내용 |
|---|---|---|
| api → transcoder | `transcode.request` | `{ filmId, sourceKey }` |
| transcoder → api | `transcode.result` | `{ filmId, status: SUCCEEDED \| FAILED, durationMillis, failureReason }` |

계약을 바꾸면 `../../transcoder/docs/architecture.md`와 양쪽 메시지 DTO를 같은 커밋에서 수정한다.

## 흐름 예시: 로그인

```
AuthController (presentation)
  → AuthUseCase.login(LoginCommand)
AuthService (application)
  → UserRepository.findByEmail(email)          → UserRepositoryAdapter (infrastructure/persistence)
  → user.isLocked(now), passwordEncoder.matches(...)
  → user.recordLoginFailure(now) 또는 user.recordLoginSuccess(now)
  → LoginTokenIssuer: JwtProvider.createAccessToken(...), RefreshTokenRepository.save(...)
AuthController
  → LoginResponse(accessToken) + Set-Cookie: refresh_token
```

## 테스트

| 대상 | 방법 |
|---|---|
| domain | Spring 없이 순수 단위 테스트 |
| application/service | 리포지토리를 Mockito로 대체, 고정 `Clock` |
| presentation + persistence | `@SpringBootTest` + MockMvc + Testcontainers PostgreSQL로 API 시나리오 |
| messaging | Testcontainers RabbitMQ |
| 의존성 규칙 | `LayerDependencyTest`(ArchUnit) |
