# transcoder 아키텍처

헥사고날 아키텍처(Ports and Adapters)를 따른다. 변환 흐름을 가운데 두고,
RabbitMQ·스토리지·FFmpeg는 바깥의 어댑터로 붙인다. DB와 웹 API는 없다 (웹 서버는 헬스 체크용).

## 의존성 규칙

```
adapter (in / out)  ──▶  application (port, service)  ──▶  domain
```

- 의존은 바깥에서 안쪽으로만 향한다.
- application은 FFmpeg, RabbitMQ, 스토리지를 직접 쓰지 않고 out 포트를 호출한다.
- adapter끼리는 서로 참조하지 않는다.

| 레이어 | 의존 가능 | 의존 금지 |
|---|---|---|
| domain | JDK, Lombok | Spring, AMQP, 다른 레이어 |
| application | domain, Spring(`@Service`), `java.nio.file` | adapter, AMQP, 프로세스 실행 |
| adapter | application의 port, domain | 다른 adapter |

## 패키지 구조

```
com.threem.transcoder
├── global/
│   └── config/                  # Clock, RabbitMQ 공통 설정, TranscodeProperties
└── transcode/
    ├── domain/                  # TranscodeJob, Rendition, TranscodeFailure
    ├── application/
    │   ├── port/in/             # TranscodeUseCase + TranscodeCommand
    │   ├── port/out/            # DownloadSourcePort, ProbeVideoPort, EncodeHlsPort, UploadHlsPort, PublishResultPort
    │   └── service/             # TranscodeService
    └── adapter/
        ├── in/messaging/        # TranscodeRequestListener
        ├── out/ffmpeg/          # FfprobeAdapter, FfmpegHlsEncoder
        ├── out/storage/         # S3StorageAdapter (로컬은 MinIO)
        └── out/messaging/       # TranscodeResultPublisher
```

## 레이어별 규칙

### domain

- `TranscodeJob`: `filmId`, `sourceKey`를 갖고 출력 키(`films/{filmId}/hls/`)를 계산한다. 키 규칙은 여기 한 곳에만 둔다.
- `Rendition`: 화질 하나(해상도, 비트레이트). 목록은 설정에서 받아 만든다.
- 실패는 두 종류로 나눈다.
  - **영구 실패**: 다시 해도 안 되는 경우 (손상된 파일, 지원하지 않는 코덱) → `FAILED` 결과로 api에 알린다.
  - **일시적 실패**: 다시 하면 될 수 있는 경우 (스토리지·네트워크 오류) → 재시도한다.

### application (TranscodeService)

- 흐름:
  1. 임시 작업 디렉터리 생성
  2. `DownloadSourcePort` — 원본을 작업 디렉터리로 받기
  3. `ProbeVideoPort` — 재생 시간 추출
  4. `EncodeHlsPort` — 화질별 HLS 생성
  5. `UploadHlsPort` — 결과를 스토리지에 올리기
  6. `PublishResultPort` — 성공 결과 발행
- 임시 디렉터리는 service가 만들고 `finally`에서 반드시 지운다.
- 영구 실패면 `FAILED` 결과를 발행하고 정상 종료한다. 일시적 실패면 예외를 그대로 던져 메시지가 다시 전달되게 한다.
- 같은 요청을 다시 처리해도 안전해야 한다 (멱등). 출력 경로를 덮어쓰는 방식으로 재처리한다.

### adapter/in/messaging

- prefetch는 1로 두고, 동시 처리 수는 설정값으로 제한한다. 변환은 CPU를 오래 쓴다.
- 유스케이스가 정상 종료하면(성공이든 영구 실패 결과 발행이든) ack 한다.
- 예외가 나면 재시도하고, 정해진 횟수를 넘으면 DLQ로 보낸다.

### adapter/out/ffmpeg

- `ProcessBuilder`에 인자 리스트로 실행한다. 셸 문자열로 조합하지 않는다.
- 타임아웃을 두고, 종료 코드를 검사하고, stderr는 로그로 남긴다.
- 세그먼트 길이, 코덱 옵션 같은 FFmpeg 세부 설정은 이 어댑터와 설정값에만 둔다.
  application은 `Rendition` 목록만 넘긴다.
- 실행 파일 경로는 설정값으로 둔다 (로컬: `/opt/homebrew/bin/ffmpeg`).

### adapter/out/storage, adapter/out/messaging

- storage: AWS SDK S3 구현 하나만 둔다. 로컬은 MinIO(S3 호환), 운영은 S3를 쓰고
  엔드포인트·자격 증명 같은 설정값만 프로필별로 다르다.
- messaging: `RabbitTemplate`으로 결과를 발행한다. 메시지 DTO는 이 패키지에 둔다.

## 메시지 계약 (api)

| 방향 | 큐 | 내용 |
|---|---|---|
| api → transcoder | `transcode.request` | `{ filmId, sourceKey }` |
| transcoder → api | `transcode.result` | `{ filmId, status: SUCCEEDED \| FAILED, durationMillis, failureReason }` |

계약을 바꾸면 `../../api/docs/architecture.md`와 양쪽 메시지 DTO를 같은 커밋에서 수정한다.

## 테스트

| 대상 | 방법 |
|---|---|
| domain, application/service | 포트를 가짜 구현으로 대체해 FFmpeg·RabbitMQ 없이 흐름 검증 (성공, 영구 실패, 일시적 실패, 임시 파일 정리) |
| adapter/out/ffmpeg | `ffmpeg -f lavfi -i testsrc=duration=3` 같은 몇 초짜리 샘플로 실제 실행. 큰 영상 파일은 커밋하지 않는다 |
| adapter/in·out/messaging | Testcontainers RabbitMQ |
| 의존성 규칙 | ArchUnit 테스트로 위 표의 규칙을 검증 |
