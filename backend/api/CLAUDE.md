# api

회원, 영상 등록·메타데이터, 시청을 담당하는 REST API 서버.
공통 규칙은 `../CLAUDE.md`를 따른다.

- 포트 8080, 패키지 `com.threem.api`
- 인프라: PostgreSQL `threem_api` (localhost:5433), RabbitMQ (localhost:5672)

## 명령어

```bash
./gradlew bootRun        # local 프로필, ../compose.yaml 인프라 필요
./gradlew bootTestRun    # compose 없이 Testcontainers로 실행
./gradlew test --tests "*.FilmServiceTest"
```

## 패키지 구조 (package-by-feature)

```
com.threem.api
├── global/      # config, security(JWT 발급·검증), 예외 처리, 공통 응답
├── user/        # 회원, 로그인, 역할(VIEWER / CREATOR / ADMIN)
├── film/        # 영상 등록, 메타데이터, 트랜스코딩 요청·결과 처리, 시청
└── storage/     # StorageService 추상화 (local / s3)
```

- 각 feature 안은 `controller` / `service` / `domain`(Entity, Repository) / `dto`로 나눈다.
- feature 간 참조는 service를 통해서만 한다. 다른 feature의 Repository를 직접 주입하지 않는다.

## 외부 연동

- **→ transcoder** (RabbitMQ): 변환 요청. 파일이 아니라 `filmId`와 원본 스토리지 키만 보낸다.
- **← transcoder** (RabbitMQ): 변환 결과. 성공 시 `duration`, 실패 시 사유를 받는다.

## 도메인 규칙

### Film (영상 등록)

- 상태: `UPLOADED → PROCESSING → READY`, 실패 시 `FAILED`.
- 대용량 업로드는 presigned URL로 클라이언트가 스토리지에 직접 올리는 방식을 목표로 한다.
- 업로드 완료를 확인하면 transcoder에 변환을 요청하고 `PROCESSING`으로 바꾼다. 결과 메시지로 `READY`/`FAILED`가 된다.
- 스토리지 키: `films/{filmId}/original/{파일명}`, `films/{filmId}/hls/index.m3u8`.

### 시청

- 일반 VOD 방식이다. 관람객마다 독립적으로 재생하고, 일시정지·탐색이 자유롭다.
- `READY` 상태의 영상만 시청할 수 있다.
- 재생은 HLS로만 제공하고 원본 파일을 직접 서빙하지 않는다. 시청 API는 HLS 재생 URL을 내려준다.

## API 컨벤션

- 경로 prefix는 `/api/v1`.
- 에러는 `BusinessException(ErrorCode)`를 던지고 `@RestControllerAdvice`에서 `{ code, message }`로 변환한다.
- 영상 등록은 `CREATOR`만 가능하고, 수정·삭제는 소유자만 가능하다.
- Entity를 컨트롤러에서 직접 반환하지 않는다.
- 연관관계는 기본 `LAZY`. 목록 조회는 fetch join이나 DTO projection으로 N+1을 피한다.
- 서비스 클래스는 `@Transactional(readOnly = true)`가 기본이고, 쓰기 메서드에만 `@Transactional`을 붙인다.

## 테스트

- 영상 상태 전이는 허용되는 전이와 거부되는 전이(예: `READY`가 아닌 영상 시청)를 모두 테스트한다.
