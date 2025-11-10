# API 요약 문서

전체 스택을 `docker compose up -d --build`로 기동한 뒤 사용할 수 있는 주요 REST API를 정리했습니다. 인증이 필요한 엔드포인트는 JWT Bearer 토큰을 요구합니다. Swagger UI(`/swagger`) 혹은 OpenAPI 스펙(`/openapi`)에서도 동일한 정보를 확인할 수 있습니다.

## 인증 / 사용자

| 메서드 | 경로 | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 회원가입 | - |
| POST | `/api/auth/login` | 로그인, JWT 발급 | - |
| GET | `/api/users/me` | 내 프로필 조회 | Bearer |
| PUT | `/api/users/me` | 내 프로필 수정 | Bearer |

## 문제 (Problems)

| 메서드 | 경로 | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/problems` | 문제 목록 조회 | - |
| GET | `/api/problems/{idOrSlug}` | 문제 상세 조회 (UUID 또는 slug) | - |
| POST | `/api/problems` | 문제 생성 | Bearer + ADMIN |
| PUT | `/api/problems/{id}` | 문제 수정 (부분 수정 가능) | Bearer + ADMIN |
| DELETE | `/api/problems/{id}` | 문제 삭제 | Bearer + ADMIN |

### 요청/응답 스키마

- `CreateProblemRequest`
  ```json
  {
    "title": "스택 괄호 검사",
    "slug": "stack-parentheses",
    "description": "...",
    "difficulty": "MEDIUM",
    "language": "KOTLIN",
    "tags": ["kotlin", "stack"],
    "testFiles": {
      "src/test/kotlin/StackTest.kt": "import org.junit.jupiter.api.Test ..."
    },
    "starterCode": "class StackSolver { ... }"
  }
  ```

- `UpdateProblemRequest`는 위 필드 전부(테스트 파일/시작 코드 포함)를 선택적으로 포함할 수 있습니다.

- `ProblemResponse`
  ```json
  {
    "id": "582df06a-dd24-4e04-80c8-afad5d356f2b",
    "title": "장바구니 서비스 구현",
    "slug": "kotlin-shopping-cart",
    "description": "...",
    "difficulty": "MEDIUM",
    "language": "KOTLIN",
    "tags": ["kotlin", "service", "testing"],
    "starterCode": "class ShoppingCart { ... }"
  }
  ```

## 제출 / 실행

| 메서드 | 경로 | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/execute` | 코드 즉시 실행 (테스트 명령 사용) | Bearer |
| POST | `/api/submissions` | 문제 제출 및 평가 시작 | Bearer |

- `ExecuteCodeRequest`
  ```json
  {
    "language": "PYTHON",
    "files": {"main.py": "print('hello')"},
    "testCommand": "python main.py"
  }
  ```

## 대시보드 / 통계

| 메서드 | 경로 | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/dashboard/stats` | 전역 통계 | - |
| GET | `/api/leaderboard` | 리더보드 | - |
| GET | `/metrics` | Prometheus 메트릭 | - |
| GET | `/health` | 헬스체크 | - |

---

> **참고**: DB에는 Exposed가 자동으로 시드한 장바구니 문제 1건이 존재합니다. ADMIN 계정으로 로그인 후 `POST /api/problems`를 호출해 새로운 문제를 추가할 수 있습니다.
