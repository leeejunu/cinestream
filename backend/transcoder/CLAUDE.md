# transcoder

업로드된 원본 영상을 FFmpeg로 HLS로 변환하는 워커.
공통 규칙은 `../CLAUDE.md`를 따른다.

- 포트 8082 (헬스 체크용), 패키지 `com.threem.transcoder`
- 인프라: RabbitMQ (localhost:5672). **DB를 갖지 않는다.**
- 로컬에 FFmpeg / ffprobe 필요 (`/opt/homebrew/bin/ffmpeg`)

## 명령어

```bash
./gradlew bootRun        # local 프로필, ../compose.yaml 인프라 필요
./gradlew bootTestRun    # compose 없이 Testcontainers로 실행
```

## 처리 흐름

1. RabbitMQ에서 변환 요청 수신 (`filmId`, 원본 스토리지 키)
2. 스토리지에서 원본을 임시 디렉터리로 다운로드
3. ffprobe로 `duration` 추출
4. FFmpeg로 화질별(예: 1080p / 720p / 480p) HLS 생성
5. 결과를 `films/{filmId}/hls/`에 업로드
6. 결과 메시지 발행: 성공 시 `duration`, 실패 시 사유

## 패키지 구조

```
com.threem.transcoder
├── global/      # config (RabbitMQ, 설정값)
├── job/         # 요청 수신, 결과 발행, 작업 흐름 조율
├── ffmpeg/      # FFmpeg / ffprobe 프로세스 실행 래퍼
└── storage/     # 원본 다운로드, HLS 업로드
```

## 규칙

- 영상 상태는 api가 관리한다. transcoder는 결과를 메시지로만 알리고 다른 서비스를 직접 호출하지 않는다.
- 같은 요청이 다시 와도 안전해야 한다 (멱등). 결과 경로를 덮어쓰는 방식으로 재처리한다.
- 변환은 CPU를 오래 쓰므로 동시 처리 수를 설정값으로 제한하고, 메시지는 한 번에 하나씩 가져온다 (prefetch 1).
- 메시지 ack는 결과 발행까지 끝난 뒤에 한다. 처리 중 죽으면 메시지가 다시 전달돼야 한다.
- 성공이든 실패든 임시 파일은 반드시 정리한다.
- FFmpeg 실행 파일 경로, 화질 프리셋, 세그먼트 길이는 설정값으로 둔다.
- FFmpeg 명령은 인자 배열로 실행한다. 셸 문자열로 조합하지 않는다.

## 테스트

- FFmpeg가 필요한 테스트는 `ffmpeg -f lavfi -i testsrc` 등으로 몇 초짜리 샘플을 만들어 쓴다.
  큰 영상 파일을 저장소에 커밋하지 않는다.
