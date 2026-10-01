# CLAUDE.md

독립영화 VOD 플랫폼 모노레포.

- `backend/` — 백엔드 서비스. `backend/CLAUDE.md` 참고
- `frontend/` — 프론트엔드

## 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/) 형식을 따른다.

```
<type>(<scope>): <요약>
```

- type: `feat` 기능 추가, `fix` 버그 수정, `refactor` 동작 변화 없는 구조 개선, `docs` 문서,
  `test` 테스트, `build` 빌드·의존성, `ci` CI 설정, `chore` 그 외 잡무
- scope: 변경한 서비스 (`api`, `transcoder`, `frontend`). 여러 곳에 걸치면 생략한다.
- 요약은 한국어로, 마침표 없이 쓴다. 본문에는 무엇을 왜 바꿨는지 적는다.

예: `feat(api): 영상 업로드 presigned URL 발급`
