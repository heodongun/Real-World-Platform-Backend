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

# View backend logs
docker compose logs -f backend

# Health check
curl http://localhost:8080/health

# Stop all services
docker compose down

# Clean rebuild
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
- **Users**: id, username, email, password_hash, role, created_at
- **Problems**: id, title, description, difficulty, test_cases, languages, created_at
- **Submissions**: id, user_id, problem_id, language, source_code, status, score, feedback, submitted_at
- **Executions**: id, execution_id, language, files, status, output, error, metrics, created_at

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
EXECUTION_TIMEOUT=30  # seconds
MAX_MEMORY_MB=512
MAX_CPU_SHARES=512
EXECUTION_WORKSPACE=/tmp/executions
```

### Service Registry Initialization

`ServiceRegistry.kt` resolves configuration with fallback priority:
1. Environment variables (e.g., `JWT_SECRET`)
2. `application.conf` properties (e.g., `jwt.secret`)
3. Hardcoded defaults (development only)

## API Endpoints

### Authentication
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - Login (returns JWT)

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

1. **Prerequisites**: JDK 17, Docker with socket access (`/var/run/docker.sock`)

2. **Database Setup**:
   ```bash
   docker compose up -d postgres redis
   ```

3. **Environment Config**:
   ```bash
   cp .env.example .env
   # Edit .env: set POSTGRES_HOST=localhost, REDIS_HOST=localhost
   ```

4. **Run Application**:
   ```bash
   ./gradlew run
   ```

5. **Verify**:
   ```bash
   curl http://localhost:8080/health
   # Expected: {"status":"UP"}
   ```

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

## Common Issues

### Port Conflicts
If port 8080/5432/6379 is occupied:
```bash
# Change in docker-compose.yml or .env
# Example: "8081:8080" for backend
```

### Docker Socket Permission
On Linux, if "Cannot connect to Docker daemon":
```bash
sudo usermod -aG docker $USER
# Log out and back in
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
