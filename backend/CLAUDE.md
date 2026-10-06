# CLAUDE.md

독립영화 VOD 플랫폼의 백엔드 (MSA). 크리에이터가 영상을 등록하면
관람객이 각자 원하는 때에 독립적으로 시청한다.

## 서비스

작업할 서비스의 CLAUDE.md를 먼저 읽는다. 서비스별 아키텍처와 규칙은 그 파일에만 있다.

| 서비스 | 역할 | 문서 |
|---|---|---|
| api | 회원, 영상 등록·메타데이터, 시청 | `api/CLAUDE.md` |
| transcoder | FFmpeg로 원본 → HLS 변환 | `transcoder/CLAUDE.md` |

- 프론트엔드: `../frontend`
- 로컬 인프라(PostgreSQL, RabbitMQ, MinIO): `compose.yaml` — `backend/`에서 `docker compose up -d`

## 서비스 간 규칙

- 각 서비스는 독립된 Gradle 프로젝트다. 멀티모듈이나 공용 `common` 모듈로 묶지 않는다.
- 모든 서비스는 헥사고날 아키텍처를 따른다. 상세는 각 서비스의 `docs/architecture.md`.
- 서비스끼리 코드를 공유하지 않는다. 메시지 같은 계약은 양쪽에 DTO를 따로 두고, 바꿀 때 같은 커밋에서 함께 수정한다.
- 서비스마다 DB를 따로 쓰고, 다른 서비스의 DB에 직접 접근하지 않는다.
- 저장소는 `3m/` 전체가 하나의 GitHub 레포다.

## 공통 환경

- Java 21, Spring Boot 4.1, Gradle (Groovy DSL). Gradle 명령은 각 서비스 디렉터리 안에서 실행한다.
- 시스템 `java`가 PATH에 없다: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew ...`

## 공통 컨벤션

- 들여쓰기 스페이스 4칸, 생성자 주입만 사용, DTO는 `record`.
- Entity에 `@Setter`/`@Data` 금지. 상태 변경은 도메인 메서드로 한다.
- 시간은 `Instant`(UTC)로 다루고 `Clock` 빈으로 현재 시각을 구한다. `Instant.now()` 직접 호출과 `LocalDateTime`은 쓰지 않는다.
- 스키마 변경은 Flyway 마이그레이션으로만 한다 (`ddl-auto: validate`).
- 통합 테스트는 Testcontainers를 쓰고 H2는 쓰지 않는다. `@DisplayName`에 한글로 시나리오를 적는다.
- 비밀값은 커밋하지 않는다. `application-local.yml`의 값은 로컬 전용이다.
