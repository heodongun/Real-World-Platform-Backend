# Docker 컨테이너 실행 흐름

이 문서는 코드 제출이 들어온 이후 Docker 컨테이너에서 테스트가 실행되고 정리되는 전 과정을 정리한 것입니다. 운영자가 무엇을 준비해야 하고, 어떤 시점에 컨테이너가 생성/삭제되는지 한 번에 파악할 수 있습니다.

## 전체 시퀀스

1. **제출 수신**  
   - `POST /api/submissions` → `SubmissionService`가 DB에 제출을 기록하고 비동기 코루틴을 시작합니다.
2. **테스트 실행 준비 (`DockerExecutorService`)**
   - 문제에 저장된 `testFiles`, 사용자가 제출한 파일, 언어별 빌드 템플릿(Gradle Wrapper 또는 `requirements.txt`)을 하나의 맵으로 병합합니다.
3. **DockerManager 실행**
   - `/tmp/executions/<submissionId>` (컨테이너 내부) / `<repo>/executions/<submissionId>` (호스트) 경로에 모든 파일을 생성
   - 언어별 이미지(`coding-platform-<language>:latest`) 존재 여부 확인 후, 없으면 자동 빌드
   - `gradle test --no-daemon` 또는 `pytest --junitxml=...` 명령을 실행하는 컨테이너를 생성/시작
   - stdout/stderr를 스트리밍하면서 타임아웃(`EXECUTION_TIMEOUT`, 기본 180초)을 감시
4. **결과 수집 및 정리**
   - 테스트 결과를 `TestRunnerService`로 파싱해 `SubmissionFeedback`(통과/실패 수, 점수 등) 저장
   - 컨테이너 종료 후 `docker rm`, 워크스페이스 디렉터리 삭제
   - 최종 점수/상태를 DB에 반영

## 언어별 실행 이미지

서버 컨테이너 안에서 runner 이미지를 빌드할 수 있도록 템플릿을 `/app/docker-templates/<language>`로 복사해 두었습니다.  
호스트 측에서는 **최소 한 번** 다음 명령을 실행해 이미지를 준비하세요:

```bash
docker build -t coding-platform-kotlin:latest src/main/resources/docker/kotlin
docker build -t coding-platform-java:latest   src/main/resources/docker/java
docker build -t coding-platform-python:latest src/main/resources/docker/python
```

> runner 이미지에는 Gradle, pytest 등 테스트 실행에 필요한 도구와 `entrypoint.sh`만 포함되어 있습니다. 제출마다 새 컨테이너가 생성되므로 캐시된 이미지만 공유됩니다.

## 환경 변수 요약

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `EXECUTION_WORKSPACE` | `/tmp/executions` | 컨테이너 내부 작업 디렉터리 |
| `DOCKER_HOST_WORKSPACE` | `./executions` | 호스트에 마운트되는 경로 (`docker-compose.yml`이 있는 `coding-platform-backend/` 기준 상대 경로) |
| `EXECUTION_TIMEOUT` | `180` | 한 제출당 최대 실행 시간(초) |
| `MAX_MEMORY_MB` | `2048` | 컨테이너 메모리 제한 |
| `MAX_CPU_SHARES` | `512` | 컨테이너 CPU shares (상대적 가중치) |
| `DOCKER_TEMPLATES_DIR` | `/app/docker-templates` | runner Dockerfile 템플릿 위치 (컨테이너 내부) |

## 보안 / 리소스 제약

- `no-new-privileges`, `cap-drop ALL`, read/write 제한 볼륨만 허용
- 네트워크 모드는 기본 `bridge`이지만 외부 접근은 없고, 테스트 파일 외 파일 접근은 작업 디렉터리로 제한
- 제출별 컨테이너 이름 패턴: `exec-<submissionId>`, 테스트 종료 후 즉시 삭제

## 트러블슈팅

| 증상 | 조치 |
| --- | --- |
| `No such image: coding-platform-...` | 위 runner 빌드 명령을 실행하여 이미지를 준비 |
| 제출 상태가 `ERROR` + 로그 비어 있음 | `docker compose logs backend -f`로 Gradle/Pytest 로그 확인 |
| 실행 시간이 길어 `TIMEOUT` 발생 | 문제 요구사항/테스트를 최적화하거나 `EXECUTION_TIMEOUT` 재조정 |
| 작업 디렉터리가 남아 있음 | 비정상 종료 시 `executions/` 하위 디렉터리를 수동 삭제 |

## 운영 체크리스트

- `docker compose up -d --build` 이후 `/swagger`에서 OpenAPI 스키마가 최신인지 확인
- 새로운 언어/문제 추가 전 runner 이미지를 빌드 (상단 명령 참고)
- 테스트가 통과되면 `/tmp/executions` 및 호스트 `executions/`에 잔여 파일이 없는지 수시로 점검
