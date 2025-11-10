# 테스트 점수 산정 규칙 (2025-11 업데이트)

## 변경 개요
- 문제 생성/수정 시 `evaluationCriteria`, `performanceTarget` 필드가 더 이상 필요하지 않습니다.
- 모든 문제는 최소 1개의 테스트 파일을 반드시 포함해야 하며, 경로와 파일 내용이 모두 채워져야 합니다.
- 제출 평가 결과는 **테스트 통과율(0~100)** 만을 기반으로 하며, 추가적인 코드 품질/성능 분석은 수행하지 않습니다.

## 점수 계산 방식
| 구분 | 설명 |
| --- | --- |
| 총 테스트 수 | 문제에 포함된 테스트 파일(JUnit/Pytest)에서 검출된 전체 케이스 수 |
| 통과 테스트 수 | 성공한 테스트 케이스 수 |
| 실패 테스트 수 | 실패한 테스트 케이스 수 |
| 점수 | `round((passed / total) * 100)` (총 테스트가 0인 경우 0점) |

`SubmissionFeedback` 예시:
```json
{
  "totalTests": 4,
  "passedTests": 3,
  "failedTests": 1,
  "passRate": 0.75,
  "score": 75,
  "status": "FAILED",
  "testResults": {
    "passed": 3,
    "failed": 1,
    "total": 4,
    "details": [...]
  },
  "output": "...gradle test logs...",
  "message": "1개의 테스트가 실패했습니다."
}
```

## 문제 생성 시 유의사항
1. `testFiles` 키 안에 **모든 테스트 파일**을 포함합니다. (예: `src/test/kotlin/...`, `tests/test_*.py`)
2. 테스트 파일 내용에는 필요한 의존성 import를 직접 포함합니다.
3. `starterCode`는 선택 사항이며, 사용자에게 바로 제공됩니다.
4. API 응답(`ProblemResponse`)에서는 테스트 파일이나 점수 관련 필드를 노출하지 않습니다.

## 운영 체크리스트
- [ ] 새 문제 등록 시 테스트 파일 개수/내용 검증
- [ ] 제출이 `ERROR` 상태일 경우 Docker 로그(`docker compose logs backend`) 확인
- [ ] Swagger(`/swagger`)와 OpenAPI(`/openapi`)가 최신 스키마(테스트 기반)인지 확인
- [ ] 언어별 실행 이미지를 미리 빌드  
  ```bash
  docker build -t coding-platform-kotlin:latest src/main/resources/docker/kotlin
  docker build -t coding-platform-java:latest src/main/resources/docker/java
  docker build -t coding-platform-python:latest src/main/resources/docker/python
  ```
