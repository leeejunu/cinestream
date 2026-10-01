# CLAUDE.md

독립영화 VOD 플랫폼 모노레포.

- `backend/` — 백엔드 서비스. `backend/CLAUDE.md` 참고
- `frontend/` — 프론트엔드

## 브랜치

- `main`: 배포용. 직접 커밋하지 않는다.
- `dev`: 개발 브랜치. 배포할 때 `main`으로 머지한다.
- `feat/{기능}`: 기능 개발 브랜치. `dev`에서 따고, 작업을 이 브랜치에 푸시한 뒤 `dev`로 머지한다.
  기능 이름은 영어 kebab-case로 짓는다 (예: `feat/film-upload`).

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
