# api

회원, 영상 메타데이터, 상영회, 상영관 입장을 담당하는 REST API 서버.
공통 규칙은 `../CLAUDE.md`를 따른다.

- 포트 8080, 패키지 `com.threem.api`
- 인프라: PostgreSQL `threem_api` (localhost:5433), RabbitMQ (localhost:5672)

## 명령어

```bash
./gradlew bootRun        # local 프로필, ../compose.yaml 인프라 필요
./gradlew bootTestRun    # compose 없이 Testcontainers로 실행
./gradlew test --tests "*.ScreeningServiceTest"
```

## 패키지 구조 (package-by-feature)

```
com.threem.api
├── global/      # config, security(JWT 발급·검증), 예외 처리, 공통 응답
├── user/        # 회원, 로그인, 역할(VIEWER / CREATOR / ADMIN)
├── film/        # 영상 업로드, 메타데이터, 트랜스코딩 요청·결과 처리
├── screening/   # 상영회 일정, 상태 전이, 입장
└── storage/     # StorageService 추상화 (local / s3)
```

- 각 feature 안은 `controller` / `service` / `domain`(Entity, Repository) / `dto`로 나눈다.
- feature 간 참조는 service를 통해서만 한다. 다른 feature의 Repository를 직접 주입하지 않는다.

## 외부 연동

- **→ transcoder** (RabbitMQ): 변환 요청. 파일이 아니라 `filmId`와 원본 스토리지 키만 보낸다.
- **← transcoder** (RabbitMQ): 변환 결과. 성공 시 `duration`, 실패 시 사유를 받는다.
- **chat**: api가 발급한 JWT를 chat이 검증한다. 토큰 클레임을 바꾸면 chat도 함께 수정한다.

## 도메인 규칙

### Film (영상)

- 상태: `UPLOADED → PROCESSING → READY`, 실패 시 `FAILED`.
- 대용량 업로드는 presigned URL로 클라이언트가 스토리지에 직접 올리는 방식을 목표로 한다.
- 업로드 완료를 확인하면 transcoder에 변환을 요청하고 `PROCESSING`으로 바꾼다. 결과 메시지로 `READY`/`FAILED`가 된다.
- 스토리지 키: `films/{filmId}/original/{파일명}`, `films/{filmId}/hls/index.m3u8`.
- `READY` 상태의 영상만 상영회에 등록할 수 있다.

### Screening (상영회)

- 상태: `SCHEDULED → LIVE → ENDED`, 시작 전에만 `SCHEDULED → CANCELED`.
- `endAt = startAt + film.duration`. 시작 시각은 과거일 수 없다.
- 수정·취소는 소유 크리에이터만, 시작 전에만 가능하다.
- SCHEDULED / LIVE / ENDED는 시각으로 결정된다. 스케줄러가 status 컬럼을 갱신하더라도
  상태 판단 로직은 `Screening` 엔티티 메서드 한 곳에 두고 `Clock` 기준으로 계산한다.
- 로비 입장은 시작 전 일정 시간부터 허용한다 (기본 10분, 설정값).

### 동기화 재생 (가장 중요)

- 상영관은 극장처럼 동작한다. 관람객은 일시정지·탐색할 수 없고 모두 같은 위치를 본다.
- **서버 시간이 기준**이다. 재생 위치 = `serverNow - startAt`.
- 서버는 재생 위치를 push하지 않는다. 입장 시 `startAt`과 `serverTime`을 내려주고 클라이언트가 시계 오차를 보정한다.
  늦게 입장한 관람객은 현재 위치로 seek한다.
- 입장 API 응답 예: `{ screeningId, hlsUrl, startAt, endAt, serverTime, duration }`.

## API 컨벤션

- 경로 prefix는 `/api/v1`.
- 에러는 `BusinessException(ErrorCode)`를 던지고 `@RestControllerAdvice`에서 `{ code, message }`로 변환한다.
- 영상 업로드와 상영회 생성은 `CREATOR`만 가능하고, 수정·삭제는 소유자만 가능하다.
- Entity를 컨트롤러에서 직접 반환하지 않는다.
- 연관관계는 기본 `LAZY`. 목록 조회는 fetch join이나 DTO projection으로 N+1을 피한다.
- 서비스 클래스는 `@Transactional(readOnly = true)`가 기본이고, 쓰기 메서드에만 `@Transactional`을 붙인다.

## 테스트

- 상영 상태 판단과 재생 위치 계산은 경계값(시작 직전·직후, 종료 직전·직후, 로비 오픈 시각)을
  고정 `Clock`으로 반드시 테스트한다.
