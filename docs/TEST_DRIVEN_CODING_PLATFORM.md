# 테스트 기반 코딩 플랫폼 가이드

## 개요

실무 중심의 코딩 테스트 플랫폼이 구현되었습니다. 문제 생성 시 테스트 코드를 포함하면, 사용자가 코드를 제출할 때 Docker 컨테이너에서 자동으로 테스트를 실행하여 채점합니다.

## 아키텍처

```
문제 생성 (Admin)
  ├─ 문제 설명 + 요구사항
  ├─ 테스트 코드 (숨김)
  └─ 시작 코드 템플릿 (선택)
       ↓
사용자 제출
  ├─ 사용자 작성 코드
       ↓
Docker 실행 환경
  ├─ 사용자 코드 + 테스트 파일 병합
  ├─ Gradle/Pytest로 테스트 실행
  └─ 결과 파싱 및 점수 산출
       ↓
피드백 제공
  ├─ 테스트 통과/실패 요약
  └─ 통과율 기반 점수 (0~100)
```

## 데이터베이스 마이그레이션

기존 데이터베이스에 새 필드를 추가해야 합니다:

```bash
# Docker Compose 환경에서 실행
docker exec -i coding-platform-db psql -U admin -d coding_platform < database/migrations/001_add_test_files_to_problems.sql

# 또는 로컬 PostgreSQL에서
psql -U admin -d coding_platform -f database/migrations/001_add_test_files_to_problems.sql
```

## 문제 생성 예시

> 모든 문제는 최소 1개의 테스트 파일을 포함해야 하며, 파일 경로와 내용이 모두 비어 있으면 안 됩니다.

### 1. Kotlin - 장바구니 구현

**POST /api/problems** (Admin 권한 필요)

```json
{
  "title": "장바구니 서비스 구현",
  "slug": "kotlin-shopping-cart",
  "description": "## 요구사항\n- 장바구니에 상품을 추가/삭제할 수 있어야 합니다.\n- 동일 상품 추가 시 수량이 증가해야 합니다.\n- 전체 금액과 할인 금액을 계산하는 기능을 작성하세요.",
  "difficulty": "MEDIUM",
  "language": "KOTLIN",
  "tags": ["kotlin", "service", "testing"],
  "testFiles": {
    "src/test/kotlin/ShoppingCartTest.kt": "import org.junit.jupiter.api.Test\nimport org.junit.jupiter.api.Assertions.*\n\nclass ShoppingCartTest {\n    @Test\n    fun `장바구니에 상품 추가`() {\n        val cart = ShoppingCart()\n        cart.addItem(\"사과\", 1000, 2)\n        assertEquals(2000, cart.getTotalPrice())\n    }\n\n    @Test\n    fun `동일 상품 추가 시 수량 증가`() {\n        val cart = ShoppingCart()\n        cart.addItem(\"사과\", 1000, 1)\n        cart.addItem(\"사과\", 1000, 2)\n        assertEquals(3000, cart.getTotalPrice())\n    }\n\n    @Test\n    fun `상품 삭제`() {\n        val cart = ShoppingCart()\n        cart.addItem(\"사과\", 1000, 2)\n        cart.removeItem(\"사과\")\n        assertEquals(0, cart.getTotalPrice())\n    }\n\n    @Test\n    fun `할인 적용`() {\n        val cart = ShoppingCart()\n        cart.addItem(\"사과\", 10000, 1)\n        cart.applyDiscount(10)\n        assertEquals(9000, cart.getTotalPrice())\n    }\n}"
  },
  "starterCode": "class ShoppingCart {\n    // 여기에 코드를 작성하세요\n    fun addItem(name: String, price: Int, quantity: Int) {\n        TODO(\"구현 필요\")\n    }\n\n    fun removeItem(name: String) {\n        TODO(\"구현 필요\")\n    }\n\n    fun getTotalPrice(): Int {\n        TODO(\"구현 필요\")\n    }\n\n    fun applyDiscount(percentage: Int) {\n        TODO(\"구현 필요\")\n    }\n}"
}
```

> **참고**: 현재 점수는 문제에 포함된 테스트 통과율(0~100)만을 사용합니다.

### 2. Python - 문자열 처리

**POST /api/problems**

```json
{
  "title": "문자열 뒤집기 함수 구현",
  "slug": "python-string-reverse",
  "description": "## 요구사항\n- 입력된 문자열을 뒤집어 반환하는 함수를 작성하세요.\n- 공백은 제거하지 않습니다.\n- 빈 문자열은 빈 문자열을 반환합니다.",
  "difficulty": "EASY",
  "language": "PYTHON",
  "tags": ["python", "string"],
  "testFiles": {
    "test_solution.py": "import pytest\nfrom solution import reverse_string\n\ndef test_basic_string():\n    assert reverse_string(\"hello\") == \"olleh\"\n\ndef test_with_spaces():\n    assert reverse_string(\"hello world\") == \"dlrow olleh\"\n\ndef test_empty_string():\n    assert reverse_string(\"\") == \"\"\n\ndef test_single_char():\n    assert reverse_string(\"a\") == \"a\""
  },
  "starterCode": "def reverse_string(text: str) -> str:\n    # 여기에 코드를 작성하세요\n    pass"
}
```

## 사용자 제출 프로세스

### 1. 문제 조회

**GET /api/problems/{slug}**

응답에서 `starterCode`를 받아 사용자에게 제공합니다 (테스트 파일은 숨김):

```json
{
  "id": "uuid",
  "title": "장바구니 서비스 구현",
  "slug": "kotlin-shopping-cart",
  "description": "...",
  "difficulty": "MEDIUM",
  "language": "KOTLIN",
  "tags": ["kotlin", "service"],
  "starterCode": "class ShoppingCart { ... }"
}
```

### 2. 코드 작성 및 제출

**POST /api/submissions** (JWT 필요)

```json
{
  "problemId": "uuid",
  "files": {
    "ShoppingCart.kt": "class ShoppingCart {\n    private val items = mutableMapOf<String, CartItem>()\n    private var discountPercentage = 0\n\n    fun addItem(name: String, price: Int, quantity: Int) {\n        val existing = items[name]\n        if (existing != null) {\n            items[name] = existing.copy(quantity = existing.quantity + quantity)\n        } else {\n            items[name] = CartItem(name, price, quantity)\n        }\n    }\n\n    fun removeItem(name: String) {\n        items.remove(name)\n    }\n\n    fun getTotalPrice(): Int {\n        val subtotal = items.values.sumOf { it.price * it.quantity }\n        return subtotal - (subtotal * discountPercentage / 100)\n    }\n\n    fun applyDiscount(percentage: Int) {\n        discountPercentage = percentage\n    }\n\n    private data class CartItem(\n        val name: String,\n        val price: Int,\n        val quantity: Int\n    )\n}"
  }
}
```

응답:

```json
{
  "success": true,
  "data": {
    "submissionId": "uuid",
    "status": "PENDING",
    "message": "제출이 접수되었습니다. 채점이 진행 중입니다."
  }
}
```

### 3. 결과 확인

**GET /api/submissions/{id}**

```json
{
  "id": "uuid",
  "userId": "uuid",
  "problemId": "uuid",
  "status": "COMPLETED",
  "score": 100,
  "feedback": {
    "totalTests": 4,
    "passedTests": 4,
    "failedTests": 0,
    "passRate": 1.0,
    "score": 100,
    "status": "SUCCESS",
    "testResults": {
      "passed": 4,
      "failed": 0,
      "total": 4,
      "details": [
        {
          "testId": "0",
          "name": "장바구니에 상품 추가",
          "status": "PASSED",
          "duration": 12
        },
        {
          "testId": "1",
          "name": "동일 상품 추가 시 수량 증가",
          "status": "PASSED",
          "duration": 8
        },
        {
          "testId": "2",
          "name": "상품 삭제",
          "status": "PASSED",
          "duration": 5
        },
        {
          "testId": "3",
          "name": "할인 적용",
          "status": "PASSED",
          "duration": 7
        }
      ],
      "coverage": {
        "line": 85,
        "branch": 80
      }
    },
    "output": "...BUILD SUCCESSFUL...",
    "message": "모든 테스트를 통과했습니다."
  },
  "createdAt": "2024-11-04T12:00:00Z",
  "updatedAt": "2024-11-04T12:00:15Z"
}
```

## Docker 실행 과정

### 1. 파일 병합

```
/tmp/executions/{submissionId}/
├── src/
│   ├── main/kotlin/
│   │   └── ShoppingCart.kt        (사용자 제출 코드)
│   └── test/kotlin/
│       └── ShoppingCartTest.kt     (문제에 포함된 테스트)
├── build.gradle.kts                (자동 생성)
├── settings.gradle.kts             (자동 생성)
└── gradlew                         (자동 생성)
```

### 2. 테스트 실행

```bash
# Kotlin/Java
chmod +x gradlew && ./gradlew test --no-daemon

# Python
pytest --junitxml=test-results.xml
```

### 3. 결과 파싱

- JUnit XML 파싱 (Kotlin/Java)
- Pytest 결과 파싱 (Python)
- 테스트 통과/실패 개수 계산
- 커버리지 추출
- 실행 시간 측정

## 보안 고려사항

### 테스트 파일 보호

- ✅ API 응답에서 `testFiles` 제외 (사용자에게 노출되지 않음)
- ✅ 데이터베이스에 암호화하여 저장 가능 (선택사항)
- ✅ Admin 권한이 있는 사용자만 문제 생성/수정 가능

### Docker 격리

- ✅ 네트워크 차단 (`--network=none`)
- ✅ 파일 시스템 격리
- ✅ 메모리/CPU 제한
- ✅ 실행 시간 제한 (30초)
- ✅ 권한 제한 (non-root user)

## 채점 기준

- 점수는 테스트 통과율 기반(0~100)으로 계산합니다.
- `score = (통과 테스트 수 / 전체 테스트 수) * 100`
- 테스트가 한 건도 없다면 점수는 0이며, 관리자에게 테스트 등록이 필요함을 알립니다.

## 트러블슈팅

### 1. 테스트가 실행되지 않음

```bash
# Docker 로그 확인
docker compose logs -f backend

# 테스트 파일 경로 확인
# Kotlin: src/test/kotlin/XXXTest.kt
# Python: test_xxx.py
```

### 2. 채점 결과가 이상함

```bash
# Submission 상태 확인
GET /api/submissions/{id}

# Execution 로그 확인 (데이터베이스)
SELECT * FROM executions WHERE submission_id = 'uuid';
```

### 3. Docker 이미지 재빌드

```bash
# 언어별 이미지 삭제 후 재빌드
docker rmi coding-platform-kotlin:latest
docker rmi coding-platform-java:latest
docker rmi coding-platform-python:latest

# 다음 제출 시 자동으로 재빌드됨
```

## 확장 아이디어

### 1. 다단계 테스트
- Unit Test (단위 테스트)
- Integration Test (통합 테스트)
- Performance Test (성능 테스트)

### 2. 실시간 피드백
- WebSocket으로 실행 중 로그 스트리밍
- 진행률 표시

### 3. 코드 리뷰 AI
- GPT 기반 코드 리뷰
- 개선 제안 자동 생성

### 4. 리더보드 확장
- 문제별 랭킹
- 속도/품질 별도 점수
- 배지/업적 시스템

## 참고 자료

- [CLAUDE.md](../CLAUDE.md) - 프로젝트 전체 가이드
- [README.md](../README.md) - 프로젝트 개요
- [Docker 보안 가이드](https://docs.docker.com/engine/security/)
- [JUnit 5 문서](https://junit.org/junit5/docs/current/user-guide/)
- [Pytest 문서](https://docs.pytest.org/)
