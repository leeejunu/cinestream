# api 아키텍처

헥사고날 아키텍처(Ports and Adapters)를 따른다. 비즈니스 로직을 가운데 두고,
웹·DB·메시지 큐·스토리지는 바깥의 어댑터로 붙인다.

## 의존성 규칙

```
adapter (in / out)  ──▶  application (port, service)  ──▶  domain
```

- 의존은 바깥에서 안쪽으로만 향한다. domain은 아무것도 모르고, application은 domain만 안다.
- application은 기술을 직접 쓰지 않고 out 포트(인터페이스)를 호출한다. 구현은 `adapter/out`에 있다.
- adapter끼리는 서로 참조하지 않는다. 예: 컨트롤러가 JPA 리포지토리를 직접 호출하지 않는다.

| 레이어 | 의존 가능 | 의존 금지 |
|---|---|---|
| domain | JDK, Lombok, `global/error` | Spring, JPA, Jackson, 다른 레이어 |
| application | domain, Spring(`@Service`, `@Transactional`) | adapter, JPA, Web, AMQP |
| adapter | application의 port, domain | 다른 adapter |

## 패키지 구조

```
com.threem.api
├── global/
│   ├── config/                  # Clock, RabbitMQ 공통 설정
│   ├── security/                # SecurityConfig, JWT 발급·검증
│   ├── error/                   # ErrorCode, ErrorType, BusinessException (순수 자바)
│   └── web/                     # GlobalExceptionHandler (ErrorType → HTTP 상태)
├── user/                        # 회원. film과 같은 구조
└── film/
    ├── domain/                  # Film, FilmStatus
    ├── application/
    │   ├── port/in/             # RegisterFilmUseCase, GetPlaybackUseCase, ... + Command/Query
    │   ├── port/out/            # LoadFilmPort, SaveFilmPort, RequestTranscodePort, FileStoragePort
    │   └── service/             # 유스케이스 구현
    └── adapter/
        ├── in/web/              # FilmController, 요청·응답 DTO
        ├── in/messaging/        # TranscodeResultListener
        ├── out/persistence/     # FilmJpaEntity, FilmJpaRepository, FilmPersistenceAdapter, FilmMapper
        ├── out/messaging/       # TranscodeRequestPublisher
        └── out/storage/         # LocalFileStorageAdapter, S3FileStorageAdapter
```

## 레이어별 규칙

### domain

- 순수 자바다. `@Entity`, `@Component`, `@JsonProperty` 같은 프레임워크 애너테이션을 붙이지 않는다.
- 상태 변경과 불변식 검사는 도메인 메서드 안에서 한다.
  예: `film.startProcessing()`, `film.complete(duration)`, `film.fail(reason)`.
  허용되지 않는 상태 전이는 `BusinessException`을 던진다.
- 현재 시각이 필요하면 `Instant now`를 인자로 받는다. 도메인이 `Clock`을 직접 쓰지 않는다.
- 생성은 정적 팩토리 메서드로 한다 (`Film.register(...)`). 저장 전에는 ID가 없고,
  `SaveFilmPort`가 ID가 채워진 도메인 객체를 돌려준다.
- Lombok은 `@Getter` 정도만 쓰고 `@Setter`는 쓰지 않는다.

### application

- **port/in**: 유스케이스 인터페이스. 이름은 `{동작}{대상}UseCase`.
  입력은 `record`로 된 Command(쓰기)나 Query(읽기)이고, 입력 형식 검증은 record의 compact constructor에서 한다.
- **port/out**: application이 바깥에 요구하는 기능. 이름은 기술이 아니라 의도로 짓는다
  (`SaveFilmPort` ○, `FilmJpaPort` ×). 쓰는 쪽 기준으로 잘게 나누고, 구현은 어댑터 하나가 여러 포트를 맡아도 된다.
- **service**: 유스케이스 구현. 트랜잭션 경계는 여기에 `@Transactional`로 잡는다.
  관련 유스케이스 여러 개를 한 서비스가 구현해도 되고, 커지면 나눈다.
- `Clock`은 service가 주입받아 `Instant.now(clock)` 값을 도메인에 넘긴다.
- 다른 feature의 기능이 필요하면 그 feature의 `port/in`을 호출한다. 다른 feature의 domain, out 포트, adapter를 직접 쓰지 않는다.
- 반환은 도메인 객체나 application용 결과 record로 한다. 웹 응답 DTO는 만들지 않는다.

### adapter/in

- **web**: 요청 DTO(record + Bean Validation) → Command 변환 → 유스케이스 호출 → 응답 DTO 변환.
  비즈니스 판단은 하지 않는다.
- **messaging**: RabbitMQ 메시지 → Command 변환 → 유스케이스 호출.
  같은 결과 메시지가 두 번 와도 안전해야 한다 (이미 `READY`/`FAILED`인 영상이면 무시).

### adapter/out

- **persistence**: JPA 엔티티는 `{이름}JpaEntity`로 짓고 이 패키지 밖으로 내보내지 않는다.
  도메인 ↔ JPA 엔티티 변환은 Mapper가 맡는다. Spring Data 리포지토리도 이 패키지 안에만 둔다.
  연관관계는 기본 `LAZY`이고, 목록 조회는 fetch join이나 DTO projection으로 N+1을 피한다.
- **messaging**: `RabbitTemplate`으로 발행한다. 메시지 DTO는 이 패키지에 둔다.
  DB 변경과 발행이 같이 일어나면 트랜잭션 커밋 후에 발행한다. 유실이 문제가 되면 outbox 패턴으로 바꾼다.
- **storage**: 로컬 파일시스템 구현과 S3 구현을 두고 프로필로 고른다.

### global

- `ErrorCode`는 코드, 메시지, `ErrorType`(INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, INTERNAL)을 갖는다.
  Spring 타입에 의존하지 않으므로 도메인에서 쓸 수 있다.
- HTTP 상태 변환은 `global/web/GlobalExceptionHandler`만 한다. 응답 형식은 `{ code, message }`.

## 메시지 계약 (transcoder)

| 방향 | 큐 | 내용 |
|---|---|---|
| api → transcoder | `transcode.request` | `{ filmId, sourceKey }` |
| transcoder → api | `transcode.result` | `{ filmId, status: SUCCEEDED \| FAILED, durationMillis, failureReason }` |

계약을 바꾸면 `../../transcoder/docs/architecture.md`와 양쪽 메시지 DTO를 같은 커밋에서 수정한다.

## 흐름 예시: 변환 요청 → 결과 반영

```
[업로드 완료]
FilmController (in/web)
  → ConfirmUploadUseCase
FilmService (application)
  → LoadFilmPort.load(filmId)
  → film.startProcessing()                 UPLOADED → PROCESSING
  → SaveFilmPort.save(film)                → FilmPersistenceAdapter (JPA)
  → RequestTranscodePort.request(...)      → TranscodeRequestPublisher (커밋 후 발행)

[변환 결과 수신]
TranscodeResultListener (in/messaging)
  → CompleteTranscodeUseCase
FilmService (application)
  → LoadFilmPort.load(filmId)
  → film.complete(duration) 또는 film.fail(reason)   PROCESSING → READY / FAILED
  → SaveFilmPort.save(film)
```

## 테스트

| 대상 | 방법 |
|---|---|
| domain | Spring 없이 순수 단위 테스트 |
| application/service | out 포트를 가짜 구현이나 Mockito로 대체, 고정 `Clock` |
| adapter/in/web | `@WebMvcTest` + 유스케이스 mock |
| adapter/out/persistence | `@DataJpaTest` + Testcontainers PostgreSQL |
| adapter/in·out/messaging | Testcontainers RabbitMQ |
| 의존성 규칙 | ArchUnit 테스트로 위 표의 규칙을 검증 |
