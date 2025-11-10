# API 테스트 예시 데이터

로컬에서 `docker compose up -d --build` 로 스택을 띄운 뒤, 아래 예제들을 그대로 복사해 API를 손쉽게 검증할 수 있습니다. 모든 요청은 `http://localhost:8080` 기준이며, JWT가 필요한 엔드포인트는 먼저 로그인 토큰을 발급받아야 합니다.

> `curl` 명령에서 JSON 문자열을 다룰 때는 큰따옴표 이스케이프(`\"`)에 주의하세요. 필요 시 `cat <<'EOF' ... EOF` 형태로 파일을 만들어 `--data-binary`로 보내면 편합니다.

---

## 1. 인증 / 사용자

### 1-1. 회원가입
```
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
        "email": "tester@example.com",
        "password": "Passw0rd!",
        "name": "테스터"
      }'
```

### 1-2. 로그인 → 토큰 추출
```
curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
        "email": "tester@example.com",
        "password": "Passw0rd!"
      }' \
  | jq -r '.token'
```

### 1-3. 내 프로필 조회
```
TOKEN=<JWT>
curl -s http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN"
```

---

## 2. 문제 (Problems)

### 2-1. 문제 생성 (ADMIN 권한 필요)
```
TOKEN=<ADMIN_JWT>
cat <<'EOF' >/tmp/problem.json
{
  "title": "장바구니 서비스 구현",
  "slug": "kotlin-shopping-cart",
  "description": "## 요구사항\n- 장바구니에 상품을 추가/삭제...",
  "difficulty": "MEDIUM",
  "language": "KOTLIN",
  "tags": ["kotlin", "service", "testing"],
  "testFiles": {
    "src/test/kotlin/ShoppingCartTest.kt": "import org.junit.jupiter.api.Test\n..."
  },
  "starterCode": "class ShoppingCart { /* TODO */ }"
}
EOF

curl -s -X POST http://localhost:8080/api/problems \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @/tmp/problem.json
```

### 2-2. 문제 목록 / 상세
```
curl -s http://localhost:8080/api/problems        # 목록
curl -s http://localhost:8080/api/problems/<id>   # UUID 또는 slug
```

### 2-3. 문제 수정 (ADMIN)
```
TOKEN=<ADMIN_JWT>
curl -s -X PUT http://localhost:8080/api/problems/<id> \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
        "difficulty": "HARD",
        "starterCode": "class ShoppingCart { /* updated */ }"
      }'
```

---

## 3. 제출 / 실행

### 3-1. 즉시 실행 (`/api/execute`, JWT 필요)
```
TOKEN=<USER_JWT>
curl -s -X POST http://localhost:8080/api/execute \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
        "language": "PYTHON",
        "files": { "main.py": "print(\"hello\")" },
        "testCommand": "python main.py"
      }'
```

### 3-2. 문제 제출 (`/api/submissions`)
```
TOKEN=<USER_JWT>
cat <<'EOF' >/tmp/submission.json
{
  "problemId": "20397d76-a1d6-4c50-b295-63c4e1a7bb82",
  "files": {
    "ShoppingCart.kt": "class ShoppingCart { ... }"
  }
}
EOF

curl -s -X POST http://localhost:8080/api/submissions \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @/tmp/submission.json
```

### 3-3. 제출 상태 조회
```
curl -s http://localhost:8080/api/submissions \
  -H "Authorization: Bearer $TOKEN"

curl -s http://localhost:8080/api/submissions/<submissionId> \
  -H "Authorization: Bearer $TOKEN"
```

응답 예시:
```json
{
  "id": "963040a6-11ac-490a-a947-fcb012beedfe",
  "status": "COMPLETED",
  "score": 100,
  "feedback": {
    "totalTests": 4,
    "passedTests": 4,
    "failedTests": 0,
    "passRate": 1.0,
    "status": "SUCCESS",
    "message": "모든 테스트를 통과했습니다."
  }
}
```

---

## 4. 장바구니 문제 전체 시나리오 (ADMIN + USER)

이 시나리오는 하나의 문제를 등록한 뒤, 실패 제출과 성공 제출을 순서대로 실행해 모든 상태를 확인합니다.

### 4-1. 관리자 토큰 & 문제 등록
```
ADMIN_TOKEN=$(curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"AdminPass123!"}' | jq -r .token)

cat <<'EOF' >/tmp/shopping-problem.json
{
  "title": "장바구니 서비스 구현",
  "slug": "kotlin-shopping-cart",
  "description": "## 요구사항\n- add/remove\n- discount 계산",
  "difficulty": "MEDIUM",
  "language": "KOTLIN",
  "tags": ["kotlin", "service", "testing"],
  "testFiles": {
    "src/test/kotlin/ShoppingCartTest.kt": "import org.junit.jupiter.api.Assertions.*\nimport org.junit.jupiter.api.Test\n\nclass ShoppingCartTest {\n  @Test\n  fun add_item() {\n    val cart = ShoppingCart()\n    cart.addItem(\"사과\", 1000, 2)\n    assertEquals(2000, cart.getTotalPrice())\n  }\n\n  @Test\n  fun increase_quantity() {\n    val cart = ShoppingCart()\n    cart.addItem(\"사과\", 1000, 1)\n    cart.addItem(\"사과\", 1000, 2)\n    assertEquals(3000, cart.getTotalPrice())\n  }\n\n  @Test\n  fun remove_item() {\n    val cart = ShoppingCart()\n    cart.addItem(\"사과\", 1000, 2)\n    cart.removeItem(\"사과\")\n    assertEquals(0, cart.getTotalPrice())\n  }\n\n  @Test\n  fun apply_discount() {\n    val cart = ShoppingCart()\n    cart.addItem(\"사과\", 10000, 1)\n    cart.applyDiscount(10)\n    assertEquals(9000, cart.getTotalPrice())\n  }\n}\n"
  },
  "starterCode": "class ShoppingCart {\n  fun addItem(name: String, price: Int, quantity: Int) { TODO() }\n  fun removeItem(name: String) { TODO() }\n  fun getTotalPrice(): Int { TODO() }\n  fun applyDiscount(percentage: Int) { TODO() }\n}"
}
EOF

PROBLEM_ID=$(curl -s -X POST http://localhost:8080/api/problems \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  --data-binary @/tmp/shopping-problem.json | jq -r .id)
```

### 4-2. 실패하는 제출 (테스트 일부 실패)
```
TOKEN=<USER_JWT>   # 로그인 토큰
# PROBLEM_ID는 앞 단계에서 생성된 UUID로 교체하세요.
cat <<'EOF' >/tmp/submission-fail.json
{
  "problemId": "<PROBLEM_ID>",
  "files": {
    "ShoppingCart.kt": "class ShoppingCart {\n  private data class Item(var price: Int, var quantity: Int)\n  private val items = mutableMapOf<String, Item>()\n\n  fun addItem(name: String, price: Int, quantity: Int) {\n    items[name] = Item(price, quantity) // 수량 누락\n  }\n\n  fun removeItem(name: String) { /* 미구현 */ }\n  fun getTotalPrice(): Int = items.values.sumOf { it.price * it.quantity }\n  fun applyDiscount(percentage: Int) { }\n}"
  }
}
EOF

FAIL_RES=$(curl -s -X POST http://localhost:8080/api/submissions \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @/tmp/submission-fail.json)
FAIL_ID=$(echo "$FAIL_RES" | jq -r '.data.submissionId')
sleep 10
curl -s http://localhost:8080/api/submissions/$FAIL_ID -H "Authorization: Bearer $TOKEN"
```
예상 `feedback`:
```json
{
  "totalTests": 4,
  "passedTests": 1,
  "failedTests": 3,
  "score": 25,
  "message": "3개의 테스트가 실패했습니다."
}
```

### 4-3. 성공하는 제출 (모든 테스트 통과)
```
cat <<'EOF' >/tmp/submission-pass.json
{
  "problemId": "<PROBLEM_ID>",
  "files": {
    "ShoppingCart.kt": "class ShoppingCart {\n  private data class Item(var price: Int, var quantity: Int)\n  private val items = mutableMapOf<String, Item>()\n  private var discount = 0\n\n  fun addItem(name: String, price: Int, quantity: Int) {\n    val current = items[name]\n    if (current == null) items[name] = Item(price, quantity)\n    else current.quantity += quantity\n  }\n\n  fun removeItem(name: String) { items.remove(name) }\n\n  fun getTotalPrice(): Int {\n    val subtotal = items.values.sumOf { it.price * it.quantity }\n    return subtotal - (subtotal * discount / 100)\n  }\n\n  fun applyDiscount(percentage: Int) {\n    discount = percentage.coerceIn(0, 100)\n  }\n}\n"
  }
}
EOF

PASS_RES=$(curl -s -X POST http://localhost:8080/api/submissions \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @/tmp/submission-pass.json)
PASS_ID=$(echo "$PASS_RES" | jq -r '.data.submissionId')
sleep 10
curl -s http://localhost:8080/api/submissions/$PASS_ID -H "Authorization: Bearer $TOKEN"
```
예상 `feedback`:
```json
{
  "totalTests": 4,
  "passedTests": 4,
  "failedTests": 0,
  "score": 100,
  "message": "모든 테스트를 통과했습니다."
}
```

### 4-4. 제출 목록 확인
```
curl -s http://localhost:8080/api/submissions \
  -H "Authorization: Bearer $TOKEN" | jq '.[] | {id,status,score}'
```

---

## 5. 대시보드 / 관측성

```
curl -s http://localhost:8080/api/dashboard/stats
curl -s http://localhost:8080/api/leaderboard
curl -s http://localhost:8080/metrics
curl -s http://localhost:8080/health
```

---

## 5. 운영 팁

- JWT 토큰은 `jq -r '.token'` 또는 Node/Python 등으로 쉽게 추출 가능합니다.
- 테스트용 JSON이 길다면 `/tmp`에 따로 저장하고 `--data-binary`로 보내세요.
- 문제 생성 시 테스트 파일을 최소 1개 이상 포함하지 않으면 `400` 에러가 발생합니다.
- 제출 상태가 `RUNNING`으로 오래 남아 있으면 `docker compose logs backend -f`로 Docker 실행 로그를 확인하세요.
