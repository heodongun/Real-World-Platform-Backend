# Coding Platform Backend

실무 환경을 모사한 코딩 테스트 플랫폼 백엔드 API 서버입니다. `docker compose up -d` 명령 하나로 애플리케이션, PostgreSQL, Redis, Prometheus, Grafana, 그리고 Docker 기반 코드 실행 엔진이 함께 기동됩니다.

## 구성 요소
- **언어**: Kotlin 1.9 / Ktor 2.3
- **데이터베이스**: PostgreSQL 16 (Exposed + HikariCP)
- **캐시/큐**: Redis 7 (Lettuce 클라이언트)
- **코드 실행 환경**: Docker socket을 이용한 격리 컨테이너 (Kotlin/Java/Python 이미지)
- **인증**: JWT (HMAC SHA-256)
- **관측성**: Micrometer + Prometheus + Grafana

## 빠른 시작
```bash
# 1. 환경변수 준비
cp .env.example .env
# 필요 시 .env 파일 값 수정

# 2. 코드 실행 워크스페이스 + SMTP 환경 변수 준비
mkdir -p executions
# .env 파일 안의 SPRING_MAIL_* 값을 Gmail 앱 비밀번호 기준으로 맞춰주세요.

# 3. 전체 스택 실행 (Backend + Frontend + DB + Monitoring)
docker compose up -d --build

# 4. 헬스 체크
curl http://localhost:8080/health

# 5. 로그 확인 (옵션)
docker compose logs -f backend
```

### 기본 접속 정보
- Backend API: `http://localhost:8080`
- Frontend UI (Docker): `http://localhost:${FRONTEND_PORT}` (기본 `3100`, Grafana와 포트 충돌 방지를 위해 분리)
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (ID/PW: `admin`/`admin`)

## 주요 기능
- 회원 가입 / 로그인 / 이메일 인증 / 프로필 수정 (JWT 인증)
- 문제 관리 (목록, 상세)
- 코드 제출 및 비동기 평가 (문제별 테스트 통과율 기반 점수)
- 즉시 코드 실행 API (테스트용)
- 대시보드 통계 & 리더보드 + Prometheus Metrics
- Docker 컨테이너 기반 다중 언어 실행 환경 (Kotlin/Java/Python)

## 코드 실행 아키텍처
1. 제출 생성 시 소스 파일을 격리된 작업공간에 저장
2. 언어별 Docker 이미지를 필요 시 자동 빌드
3. 메모리/CPU/네트워크 제한이 걸린 컨테이너에서 테스트 및 빌드 수행
4. 실행 로그와 테스트 결과를 파싱하여 점수/피드백 산출
5. 컨테이너 및 작업 디렉터리 정리

## 프로젝트 구조
```
coding-platform-backend/
├── build.gradle.kts
├── docker-compose.yml
├── Dockerfile
├── src/
│   ├── main/kotlin/com/codingplatform/
│   │   ├── Application.kt
│   │   ├── plugins/
│   │   ├── routes/
│   │   ├── services/
│   │   ├── executor/
│   │   └── models/
│   └── main/resources/
├── monitoring/
│   ├── prometheus/prometheus.yml
│   └── grafana/
└── database/init.sql
```

## 모니터링
- Prometheus는 `backend:8080/metrics` 엔드포인트를 스크랩합니다.
- Grafana Provisioning이 자동으로 Prometheus 데이터 소스를 등록하며, 샘플 대시보드(`Backend Overview`)를 제공합니다.

## 로컬 개발
```bash
# 의존성 다운로드 및 빌드
./gradlew build

# 애플리케이션 실행
./gradlew run

# 테스트 실행 (실제 DB/Redis가 필요합니다)
./gradlew test
```

> ⚠️ 테스트 및 애플리케이션을 로컬에서 실행하려면 PostgreSQL과 Redis가 기동되어 있어야 하며, `.env` 값이 로컬 환경에 맞게 설정되어야 합니다.

## API 요약
- `POST /api/auth/register` – 회원 가입
- `POST /api/auth/login` – 로그인
- `GET /api/problems` – 문제 목록
- `POST /api/problems` – 문제 등록 (JWT Admin)
- `PUT /api/problems/{id}` – 문제 수정 (JWT Admin)
- `DELETE /api/problems/{id}` – 문제 삭제 (JWT Admin)
- `POST /api/execute` – 코드 즉시 실행 (JWT 필요)
- `POST /api/submissions` – 평가 요청 (JWT 필요)
- `GET /api/dashboard/stats` – 대시보드 통계
- `GET /metrics` – Prometheus 메트릭
- `GET /swagger` – Swagger UI (OpenAPI 스펙은 `/openapi`)

## 추가 참고
- 코드 실행용 Docker 이미지는 최초 실행 시 자동 빌드됩니다.
- 컨테이너는 `no-new-privileges`, 캡 능력 제거, 메모리/CPU 제한, 네트워크 차단 등 보안 설정이 적용됩니다.
- 실행 결과와 피드백은 PostgreSQL에 저장되며, Redis는 향후 캐시/큐 용도로 확장할 수 있습니다.
- 테스트 점수 규칙과 샘플 테스트 시나리오는 `docs/TEST_SCORING.md`, 전체 Docker 실행 흐름은 `docs/CONTAINER_EXECUTION.md`, API별 요청/응답 예시는 `docs/API_TEST_EXAMPLES.md`에 정리되어 있습니다.
- `.env` 파일에 정의한 `DOCKER_HOST_WORKSPACE`(기본 `./executions`) 경로는 컨테이너와 공유되므로 git에 올리지 마시고, 필요 시 정기적으로 디스크를 정리하세요.
- 동일 compose 파일에서 `frontend` 서비스도 함께 기동되며, `.env`의 `FRONTEND_PUBLIC_API_BASE_URL`과 `FRONTEND_PORT`로 연결 대상을 제어할 수 있습니다.
- 회원가입은 `/api/auth/register/code` → 메일 인증 코드 입력 → `/api/auth/register` 순으로 진행됩니다. Gmail SMTP(앱 비밀번호)를 `SPRING_MAIL_*` 변수에 넣어야 발송이 정상 동작합니다.
