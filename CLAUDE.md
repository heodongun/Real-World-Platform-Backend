# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Kotlin-based coding challenge platform backend using Ktor framework. The system evaluates code submissions in isolated Docker containers and provides real-time feedback with code quality analysis, test results, and performance metrics.

**Tech Stack**: Kotlin 1.9, Ktor 2.3, PostgreSQL 16 (Exposed ORM), Redis 7, Docker API, Prometheus/Grafana

## Development Commands

### Build & Run
```bash
# Build the project
./gradlew build

# Run locally (requires PostgreSQL & Redis running)
./gradlew run

# Run tests (requires active DB/Redis)
./gradlew test

# Code style check
./gradlew ktlintCheck

# Auto-fix code style
./gradlew ktlintFormat
```

### Docker Environment
```bash
# Start full stack (app, DB, Redis, monitoring)
docker compose up -d --build

# Start with frontend included
docker compose --profile frontend up -d --build

# View backend logs
docker compose logs -f backend

# Health check
curl http://localhost:8080/health

# Stop all services
docker compose down

# Clean rebuild (removes volumes and cached data)
docker compose down -v && docker compose up -d --build
```

### Testing Single Components
```bash
# Run specific test class
./gradlew test --tests "com.codingplatform.services.AuthServiceTest"

# Run specific test method
./gradlew test --tests "com.codingplatform.services.AuthServiceTest.testUserLogin"
```

## Architecture

### Core Service Flow

```
Application.kt (entry point)
  └─> ServiceRegistry (dependency injection)
       ├─> DatabaseFactory (Exposed + HikariCP)
       ├─> JwtConfig (authentication)
       ├─> RedisClient (caching/queuing)
       └─> Business Services:
            ├─> AuthService (JWT generation/validation)
            ├─> ProblemService (CRUD + seeding)
            ├─> SubmissionService (evaluation orchestration)
            ├─> DockerExecutorService (code execution)
            └─> DashboardService (stats/leaderboard)
```

### Code Execution Pipeline

The `DockerExecutorService` coordinates three specialized services:

1. **DockerManager** (`executor/DockerManager.kt`):
   - Creates isolated workspace (`/tmp/executions/{executionId}/`)
   - Builds/caches language-specific Docker images
   - Runs containers with security constraints (no-new-privileges, cap-drop ALL, network=none)
   - Captures stdout/stderr and enforces timeout
   - Cleans up containers and workspace

2. **TestRunnerService** (`services/TestRunnerService.kt`):
   - Parses test output (JUnit XML for Kotlin/Java, pytest for Python)
   - Calculates test pass rate
   - Extracts detailed failure messages

3. **DockerExecutorService** (`services/DockerExecutorService.kt`):
   - 병합된 사용자 코드 + 문제별 테스트 파일을 Docker 컨테이너에서 실행
   - Gradle/Pytest 출력으로 테스트 통과/실패를 집계
   - 테스트 통과율(0~100)을 점수로 환산해 `SubmissionFeedback`에 저장

Language-specific runners (`executor/runners/`) extend `LanguageRunner` and define compilation + test execution commands.

### Database Schema

Tables defined in `database/tables/`:

- **Users** (`database/tables/Users.kt`):
  - Stores user accounts with authentication credentials
  - Fields: id (UUID), username, email, password_hash (BCrypt), role (USER/ADMIN), created_at
  - Role-based access control for admin operations

- **Problems** (`database/tables/Problems.kt`):
  - Coding challenge definitions with embedded test files
  - Fields: id, title, description, difficulty (EASY/MEDIUM/HARD), test_files (JSON), starter_code, languages (supported: KOTLIN/JAVA/PYTHON), created_at
  - Test files must include path and content for test execution
  - Test files are not exposed in API responses to prevent solution leakage

- **Submissions** (`database/tables/Submissions.kt`):
  - User code submissions with evaluation results
  - Fields: id, user_id (FK to Users), problem_id (FK to Problems), language, source_code, status (PENDING/SUCCESS/FAILED/ERROR), score (0-100), feedback (JSON), submitted_at
  - Score calculated as: `(passed_tests / total_tests) * 100`
  - Feedback includes detailed test results, pass rate, and execution output

- **Executions** (`database/tables/Executions.kt`):
  - Docker execution history and metrics (for immediate code execution API)
  - Fields: id, execution_id (UUID), language, files (JSON), status, output, error, execution_time_ms, memory_used_bytes, created_at
  - Used for `/api/execute` endpoint, separate from problem submissions

- **EmailVerifications** (`database/tables/EmailVerifications.kt`):
  - Email verification codes for registration
  - Fields: id, email, code (6-digit), created_at, verified_at, expires_at
  - Codes expire after configured time period
  - Two-step registration: request code → verify code → register

### Plugin System

Ktor configuration in `plugins/`:
- **Database.kt**: Exposes transaction management via `DatabaseFactory`
- **Security.kt**: JWT validation with `JwtConfig`
- **Serialization.kt**: kotlinx.serialization setup
- **StatusPages.kt**: Exception handling middleware
- **Monitoring.kt**: Micrometer + Prometheus metrics at `/metrics`
- **Routing.kt**: Route registration

## Configuration

### Environment Variables

Required in `.env` (copy from `.env.example`):

```bash
# Database
POSTGRES_DB=coding_platform
POSTGRES_USER=admin
POSTGRES_PASSWORD=<secure_password>
POSTGRES_HOST=postgres  # or localhost for local dev
POSTGRES_PORT=5432

# Redis
REDIS_HOST=redis  # or localhost for local dev
REDIS_PORT=6379

# JWT (MUST change in production)
JWT_SECRET=<256-bit-secret>
JWT_ISSUER=coding-platform
JWT_AUDIENCE=coding-platform-users
JWT_REALM=coding-platform-auth
JWT_EXP_SECONDS=21600  # 6 hours

# Application
APP_PORT=8080
APP_ENV=development

# Code execution constraints
EXECUTION_TIMEOUT=30  # seconds (180 in docker-compose)
MAX_MEMORY_MB=512  # (2048 in docker-compose)
MAX_CPU_SHARES=512
EXECUTION_WORKSPACE=/tmp/executions  # container path
DOCKER_HOST_WORKSPACE=/tmp/coding-platform-executions  # MUST be absolute host path for Docker bind mount

# SMTP / Email (required for email verification)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<your_email@gmail.com>
SPRING_MAIL_PASSWORD=<gmail_app_password>
SPRING_MAIL_STARTTLS_ENABLE=true
SMTP_FROM_EMAIL=<your_email@gmail.com>
SMTP_FROM_NAME=Coding Platform

# Frontend (for docker-compose with frontend profile)
FRONTEND_PUBLIC_API_BASE_URL=http://localhost:8080
FRONTEND_SERVER_API_BASE_URL=http://backend:8080
FRONTEND_PORT=3100  # Separate from Grafana's 3000
```

**Critical Configuration Notes**:
- `DOCKER_HOST_WORKSPACE`: MUST be an absolute path on the host machine. Docker daemon uses this to bind mount code execution workspaces into sandbox containers. Never commit this directory to git.
- SMTP credentials: For Gmail, use an App Password (not your regular password). Required for email verification workflow.
- Frontend ports: Default is 3100 to avoid conflict with Grafana's 3000.

### Service Registry Initialization

`ServiceRegistry.kt` resolves configuration with fallback priority:
1. Environment variables (e.g., `JWT_SECRET`)
2. `application.conf` properties (e.g., `jwt.secret`)
3. Hardcoded defaults (development only)

## API Endpoints

### Authentication
- `POST /api/auth/register/code` - Request email verification code (step 1)
- `POST /api/auth/register` - Complete registration with verified code (step 2)
- `POST /api/auth/login` - Login (returns JWT)
- `GET /api/users/profile` - Get current user profile (requires JWT)
- `PUT /api/users/profile` - Update user profile (requires JWT)

### Problems
- `GET /api/problems` - List all problems
- `GET /api/problems/{id}` - Problem details
- `POST /api/problems` - Create (requires JWT + Admin role)
- `PUT /api/problems/{id}` - Update (requires JWT + Admin)
- `DELETE /api/problems/{id}` - Delete (requires JWT + Admin)

### Code Execution
- `POST /api/execute` - Immediate code execution (requires JWT)
- `POST /api/submissions` - Submit solution for evaluation (requires JWT)
- `GET /api/submissions/{id}` - Submission status/results
- `GET /api/submissions/user/{userId}` - User's submission history

### Monitoring
- `GET /api/dashboard/stats` - Platform statistics
- `GET /api/dashboard/leaderboard` - Top performers
- `GET /metrics` - Prometheus metrics
- `GET /health` - Health check
- `GET /swagger` - Swagger UI (OpenAPI spec at `/openapi`)

## Security Considerations

### Docker Container Isolation
- Network disabled (`--network=none`)
- Read-only root filesystem option
- User namespace (runs as `1000:1000`, not root)
- Capabilities dropped (`--cap-drop=ALL`)
- Security opt `no-new-privileges`
- Memory/CPU limits enforced
- 30-second execution timeout

### Code Validation
`SecurityManager.kt` blocks:
- System calls (e.g., `Runtime.getRuntime().exec()`)
- File I/O outside workspace
- Network operations
- Process creation
- Reflection abuse

### Authentication
- JWT tokens with HMAC SHA-256
- Passwords hashed with BCrypt
- Role-based access control (USER/ADMIN)
- Token expiration enforced

## Monitoring

### Access Points
- **Prometheus**: http://localhost:9090 (scrapes `/metrics` every 15s)
- **Grafana**: http://localhost:3000 (admin/admin)
  - Pre-provisioned "Backend Overview" dashboard
  - Datasource auto-configured

### Key Metrics
- HTTP request rate/latency (per endpoint)
- Database connection pool stats
- Docker execution time/success rate
- JVM memory/GC metrics

## Local Development Setup

1. **Prerequisites**:
   - JDK 17
   - Docker with socket access (`/var/run/docker.sock`)
   - Gmail account with App Password (for email verification)

2. **Database Setup**:
   ```bash
   docker compose up -d postgres redis
   ```

3. **Environment Config**:
   ```bash
   cp .env.example .env
   # Edit .env:
   # - Set POSTGRES_HOST=localhost, REDIS_HOST=localhost for local dev
   # - Configure SPRING_MAIL_* variables with Gmail credentials
   # - Create workspace: mkdir -p /tmp/coding-platform-executions
   ```

4. **Run Application**:
   ```bash
   ./gradlew run
   ```

5. **Verify**:
   ```bash
   curl http://localhost:8080/health
   # Expected: {"status":"UP"}

   # Test registration flow
   curl -X POST http://localhost:8080/api/auth/register/code \
     -H "Content-Type: application/json" \
     -d '{"email":"test@example.com"}'
   # Check email for verification code
   ```

## Registration Workflow

The platform uses a two-step email verification process:

1. **Request Verification Code**:
   ```bash
   POST /api/auth/register/code
   Body: {"email": "user@example.com"}
   ```
   - Generates 6-digit verification code
   - Sends email via configured SMTP server
   - Code expires after configured time (stored in EmailVerifications table)

2. **Complete Registration**:
   ```bash
   POST /api/auth/register
   Body: {
     "email": "user@example.com",
     "username": "myusername",
     "password": "secure_password",
     "code": "123456"
   }
   ```
   - Validates verification code
   - Creates user account with BCrypt password hash
   - Returns JWT token for immediate login
   - Marks email as verified

Services involved:
- `EmailService` (`services/email/EmailService.kt`): SMTP email delivery
- `EmailVerificationService` (`services/EmailVerificationService.kt`): Code generation and validation
- `AuthService` (`services/AuthService.kt`): Registration orchestration and JWT generation

## Code Patterns

### Service Layer
All services follow constructor dependency injection:
```kotlin
class SomeService(
    private val databaseFactory: DatabaseFactory,
    private val otherService: OtherService
) {
    suspend fun operation() = databaseFactory.dbQuery {
        // Exposed DSL queries here
    }
}
```

### Route Handlers
Routes use extension functions on `Route`:
```kotlin
fun Route.someRoutes(service: SomeService) {
    authenticate {  // JWT required
        get("/api/resource") {
            val result = service.operation()
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
```

### Error Handling
`StatusPages` plugin catches exceptions globally:
- `400 Bad Request` for validation errors
- `401 Unauthorized` for JWT failures
- `500 Internal Server Error` for uncaught exceptions

## Testing

### Test Structure
```
src/test/kotlin/com/codingplatform/
└── (test files matching main structure)
```

### Database Tests
Tests require live PostgreSQL:
```kotlin
@BeforeEach
fun setup() {
    val dbFactory = DatabaseFactory(testConfig, tables)
    dbFactory.init()
}
```

### Execution Tests
Docker daemon must be accessible for integration tests involving `DockerManager`.

## Submission Evaluation Flow

The platform evaluates code submissions using a test-based scoring system:

1. **User submits code** → `POST /api/submissions`
2. **DockerExecutorService** merges user code + problem test files + build files
3. **DockerManager** creates isolated workspace in `DOCKER_HOST_WORKSPACE/{submissionId}/`
4. **Language-specific runner** executes tests (Gradle for Kotlin/Java, Pytest for Python)
5. **TestRunnerService** parses test output (JUnit XML or pytest results)
6. **Score calculation**: `round((passed_tests / total_tests) * 100)`
7. **Feedback stored** in Submissions table with detailed test results
8. **Cleanup**: Container removed, workspace deleted

See `docs/TEST_SCORING.md` for detailed scoring rules and `docs/CONTAINER_EXECUTION.md` for execution flow.

## Common Issues

### Port Conflicts
If port 8080/5432/6379/3000/3100 is occupied:
```bash
# Change in docker-compose.yml or .env
# Example: "8081:8080" for backend, "3101:3000" for frontend
```

### Docker Socket Permission
On Linux, if "Cannot connect to Docker daemon":
```bash
sudo usermod -aG docker $USER
# Log out and back in, or: newgrp docker
```

### DOCKER_HOST_WORKSPACE Issues
If submissions fail with "workspace not found":
```bash
# Ensure workspace directory exists and has proper permissions
mkdir -p /tmp/coding-platform-executions
chmod 777 /tmp/coding-platform-executions

# Verify .env has absolute host path
echo $DOCKER_HOST_WORKSPACE
# Should output: /tmp/coding-platform-executions (or your custom path)
```

### Email Verification Not Working
If registration emails aren't sent:
```bash
# Check SMTP credentials in .env
# For Gmail, use App Password (not regular password):
# 1. Enable 2FA on Gmail account
# 2. Generate App Password at https://myaccount.google.com/apppasswords
# 3. Use that password in SPRING_MAIL_PASSWORD

# Test SMTP connection
docker compose logs backend | grep "EmailService"
```

### Database Migration
Schema changes require updating:
1. Table definitions in `database/tables/`
2. `database/init.sql` if using fresh DB
3. Run migrations or rebuild: `docker compose down -v && docker compose up -d`

### Ktlint Failures
Auto-fix most issues:
```bash
./gradlew ktlintFormat
```

### Frontend Connection Issues
If frontend can't reach backend:
```bash
# Check environment variables
echo $FRONTEND_PUBLIC_API_BASE_URL  # Client-side API URL
echo $FRONTEND_SERVER_API_BASE_URL  # Server-side API URL

# For local development: http://localhost:8080
# For production: http://your-domain.com:8080 or https://api.your-domain.com
```

## Deployment Notes

- Change `JWT_SECRET` to cryptographically random 256-bit key
- Set `APP_ENV=production` to disable debug features
- Configure external PostgreSQL/Redis for persistence
- Set up Docker image registry for language runners
- Enable HTTPS/TLS termination (reverse proxy)
- Configure resource limits in production docker-compose

## Language Runner Dockerfiles

Located in `src/main/resources/docker/{kotlin,java,python}/Dockerfile`. These images are built on-demand and cached. To rebuild:
```bash
# Delete images to force rebuild
docker rmi coding-platform-kotlin:latest
docker rmi coding-platform-java:latest
docker rmi coding-platform-python:latest
```
