# 테스트 데이터 & 시나리오

이 문서는 현 프로젝트에서 바로 활용할 수 있는 기본 테스트 데이터와, `curl`을 이용해 API를 검증하는 절차를 정리한 것입니다. 모든 명령은 `docker compose up -d --build`로 스택을 띄운 뒤, 동일한 머신에서 실행합니다.

> **TIP**: 로컬 호스트에서 직접 접속이 되지 않는 환경이라면, `curlimages/curl` 컨테이너를 Compose 네트워크(`coding-platform-backend_coding-platform-network`)에 붙여 실행하면 안정적으로 테스트할 수 있습니다. 본 문서의 예제는 모두 해당 방식을 사용합니다.

## 1. 기본 사용자 시드

| 구분 | 이메일 | 비밀번호 | 역할 |
| --- | --- | --- | --- |
| 관리자 | `admin@example.com` | `AdminPass123!` | `ADMIN` |
| 일반 사용자 | `user@example.com` | `UserPass123!` | `USER` |

> 두 계정은 문서의 curl 시나리오에서 모두 생성됩니다. 이미 존재한다면 `409 Conflict` 대신 `200 OK`가 반환됩니다.

## 2. 네트워크 변수

```bash
NETWORK=coding-platform-backend_coding-platform-network
```

Compose 네트워크 이름은 `docker compose ps` 상단에 표시된 이름과 반드시 일치해야 합니다. 기본 값은 위와 같습니다.

## 3. 관리자 흐름

```bash
# 1) 관리자 회원가입 (이미 존재하면 200 OK)
docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s http://backend:8080/api/auth/register \
    -H 'Content-Type: application/json' \
    -d '{\"email\":\"admin@example.com\",\"password\":\"AdminPass123!\",\"name\":\"Admin User\"}'"

# 2) 관리자 로그인 → 토큰 추출 (python3 필요)
ADMIN_TOKEN=$(docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s http://backend:8080/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{\"email\":\"admin@example.com\",\"password\":\"AdminPass123!\"}'" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 3) 문제 목록
PROBLEMS=$(docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s http://backend:8080/api/problems")

# 4) 문제 생성
CREATE_PAYLOAD='{"title":"스택 괄호 검사","slug":"stack-parentheses","description":"스택을 이용해 괄호 문자열의 유효성을 검사하는 함수를 작성하세요.","difficulty":"MEDIUM","language":"KOTLIN","tags":["kotlin","stack"],"testFiles":{"src/test/kotlin/StackTest.kt":"import org.junit.jupiter.api.Assertions.*"}}'
CREATE_RES=$(docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s -X POST http://backend:8080/api/problems \
    -H 'Content-Type: application/json' \
    -H 'Authorization: Bearer $ADMIN_TOKEN' \
    -d '$CREATE_PAYLOAD'")
PROBLEM_ID=$(python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" <<<"$CREATE_RES")

# 5) 문제 수정
UPDATE_PAYLOAD='{"difficulty":"HARD","starterCode":"class StackSolver { /* TODO */ }"}'
UPDATE_RES=$(docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s -X PUT http://backend:8080/api/problems/$PROBLEM_ID \
    -H 'Content-Type: application/json' \
    -H 'Authorization: Bearer $ADMIN_TOKEN' \
    -d '$UPDATE_PAYLOAD'")

# 6) 문제 상세 & 삭제
DETAIL=$(docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s http://backend:8080/api/problems/$PROBLEM_ID")
DELETE_STATUS=$(docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s -o /dev/null -w '%{http_code}' -X DELETE \
    http://backend:8080/api/problems/$PROBLEM_ID \
    -H 'Authorization: Bearer $ADMIN_TOKEN'")
```

## 4. 일반 사용자 흐름

```bash
# 회원가입 & 로그인
docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s http://backend:8080/api/auth/register \
    -H 'Content-Type: application/json' \
    -d '{\"email\":\"user@example.com\",\"password\":\"UserPass123!\",\"name\":\"Regular User\"}'"

USER_TOKEN=$(docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s http://backend:8080/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{\"email\":\"user@example.com\",\"password\":\"UserPass123!\"}'" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 내 프로필 확인
docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s http://backend:8080/api/users/me \
    -H 'Authorization: Bearer $USER_TOKEN'"

# 권한 없는 문제 생성 시도 (예상: 403)
docker run --rm --network "$NETWORK" curlimages/curl:8.4.0 \
  sh -lc "curl -s -o - -w '\n%{http_code}\n' -X POST http://backend:8080/api/problems \
    -H 'Content-Type: application/json' \
    -H 'Authorization: Bearer $USER_TOKEN' \
    -d '{\"title\":\"권한 테스트\",\"slug\":\"auth-test\",\"description\":\"권한 테스트\",\"difficulty\":\"EASY\",\"language\":\"PYTHON\",\"tags\":[],\"testFiles\":{\"tests/test_sample.py\":\"def test_sample():\\n    assert True\"}}'"
```

## 5. 기대 출력 요약

| 단계 | 기대 결과 |
| --- | --- |
| 관리자 로그인 | `role: ADMIN` JWT 발급 |
| 문제 생성 | `201 Created` + 생성된 문제 JSON |
| 문제 수정 | `200 OK` + 수정된 필드 반영 |
| 문제 삭제 | `204 No Content` |
| 일반 사용자 문제 생성 | `403` + `{"error":"ADMIN 권한이 필요합니다."}` |
| `/api/users/me` | 해당 사용자 프로필 JSON |

위 명령을 순서대로 실행하면, 관리자/일반 사용자 플로우와 권한 검증을 한 번에 확인할 수 있습니다. EOF
