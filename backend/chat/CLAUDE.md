# chat

상영관 실시간 채팅과 접속자 수를 담당하는 WebSocket(STOMP) 서버.
공통 규칙은 `../CLAUDE.md`를 따른다.

- 포트 8081, 패키지 `com.threem.chat`
- 인프라: PostgreSQL `threem_chat` (localhost:5434)

## 명령어

```bash
./gradlew bootRun        # local 프로필, ../compose.yaml 인프라 필요
./gradlew bootTestRun    # compose 없이 Testcontainers로 실행
```

## 패키지 구조 (package-by-feature)

```
com.threem.chat
├── global/      # config(WebSocketConfig), STOMP 인증, 예외 처리
├── message/     # 채팅 메시지 발행·저장
└── presence/    # 상영관 접속자 수
```

## STOMP 규칙

- 연결 엔드포인트 `/ws`.
- 채팅 구독 `/topic/screenings/{id}/chat`, 발행 `/app/screenings/{id}/chat`.
- 접속자 수 등 시스템 이벤트 `/topic/screenings/{id}/events`.
- 인증은 HTTP 핸드셰이크가 아니라 STOMP `CONNECT` 헤더의 JWT로 한다. api가 발급한 토큰을
  `ChannelInterceptor`에서 검증하고, 로그인 사용자만 발행할 수 있다.

## 도메인 규칙

- 로비가 열린 뒤부터 상영 종료까지만 발행할 수 있다. 상영 시간 정보를 api에서 얻는 방법은 아직 정하지 않았다.
- 메시지는 chat DB에 저장한다 (상영 종료 후 조회·신고 대비).
- 메시지 길이 제한과 사용자별 전송 속도 제한을 둔다.
- 상영 시작 시각에 접속이 한꺼번에 몰린다는 점을 전제로 설계한다.

## 확장

- 초기에는 Spring 내장 SimpleBroker를 쓴다. 서버를 여러 대로 늘릴 때 RabbitMQ STOMP relay로 교체한다.
- 브로커 설정은 `WebSocketConfig` 한 곳에만 둔다.
- 영상 재생은 CDN과 클라이언트 계산으로 이뤄지므로, chat 서버가 재시작돼도 재생은 끊기지 않는다.
  클라이언트는 재연결만 하면 된다.
