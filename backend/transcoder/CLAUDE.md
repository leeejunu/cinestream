# transcoder

업로드된 원본 영상을 FFmpeg로 HLS로 변환하는 워커.
공통 규칙은 `../CLAUDE.md`를 따른다.

- 포트 8082 (헬스 체크용), 패키지 `com.threem.transcoder`
- 인프라: RabbitMQ (localhost:5672). **DB를 갖지 않는다.**
- 로컬에 FFmpeg / ffprobe 필요 (`/opt/homebrew/bin/ffmpeg`)
- **아키텍처는 헥사고날이다. 코드를 작성하기 전에 `docs/architecture.md`를 읽는다**
  (처리 흐름, 패키지 구조, 실패·재시도 규칙, api 메시지 계약, 테스트 방법).

## 명령어

```bash
./gradlew bootRun        # local 프로필, ../compose.yaml 인프라 필요
./gradlew bootTestRun    # compose 없이 Testcontainers로 실행
```

## 규칙

- 영상 상태는 api가 관리한다. transcoder는 결과를 메시지로만 알리고 다른 서비스를 직접 호출하지 않는다.
