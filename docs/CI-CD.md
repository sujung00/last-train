# CI/CD 파이프라인 (GitHub Actions + AWS)

> **버전**: 1.0  
> **생성일**: 2026-07-15  
> **상태**: Production Ready  
> **담당**: DevOps Team

---

## 개요

이 문서는 **Last Train Notifier** 프로젝트의 CI/CD 파이프라인을 설명합니다.

**파이프라인 흐름:**
```
develop 브랜치 push
  ↓
백엔드 테스트 (JUnit 5)
  ↓
프론트엔드 빌드 (Vite)
  ↓
Docker 빌드 (멀티아키텍처)
  ↓
AWS ECR 푸시
  ↓
AWS ECS 배포
  ↓
Slack 알림
```

---

## 1. 파이프라인 구성요소

### 1.1 Backend Tests
- **작업**: 백엔드 단위/통합 테스트 실행
- **도구**: Gradle + JUnit 5 + TestContainers
- **명령**: `./gradlew test`
- **캐시**: Gradle 의존성 캐싱 (빌드 속도 향상)
- **결과**: 테스트 리포트 업로드 (7일 보관)

### 1.2 Frontend Build Verification
- **작업**: 프론트엔드 린트 + 빌드 검증
- **도구**: ESLint + Vite + npm
- **명령**: `npm run lint && npm run build`
- **캐시**: npm 의존성 캐싱
- **결과**: 빌드 아티팩트 생성 (dist/)

### 1.3 Docker Build & Push
- **작업**: Docker 이미지 빌드 및 ECR 푸시
- **특징**:
  - 멀티아키텍처 빌드 (linux/amd64)
  - 프론트엔드/백엔드 통합 빌드
  - 레지스트리 캐싱으로 빌드 시간 단축
- **이미지 태그**: `YYYYMMDD-HHMMSS-SHORT_SHA`
- **최신 태그**: `latest`

### 1.4 ECS Deployment
- **작업**: AWS ECS 서비스 배포
- **단계**:
  1. ECS Task Definition 조회
  2. 새 Docker 이미지로 Task Definition 업데이트
  3. ECS 서비스 재배포 (`--force-new-deployment`)
  4. 배포 완료 대기 (최대 10분)

### 1.5 Notification
- **작업**: Slack 배포 결과 알림
- **정보**: Repository, Branch, Commit, Author, Image URI

---

## 2. GitHub Secrets 설정

파이프라인 실행을 위해 다음 Secrets를 GitHub 저장소에 등록해야 합니다.

| Secret | 필수 | 설명 | 예시 |
|--------|------|------|------|
| `AWS_REGION` | ✅ | AWS 지역 | `ap-northeast-2` |
| `AWS_ROLE_ARN` | ✅ | OIDC를 통한 IAM Role ARN | `arn:aws:iam::123456789:role/GitHubActionsRole` |
| `ECR_REGISTRY` | ✅ | ECR 레지스트리 주소 | `123456789.dkr.ecr.ap-northeast-2.amazonaws.com` |
| `ECS_CLUSTER_NAME` | ✅ | ECS 클러스터 이름 | `last-train-cluster` |
| `ECS_SERVICE_NAME` | ✅ | ECS 서비스 이름 | `last-train-service` |
| `ECS_TASK_DEFINITION` | ✅ | ECS Task Definition 이름 | `last-train-task-def` |
| `ECS_CONTAINER_NAME` | ✅ | 컨테이너 이름 | `last-train-app` |
| `SLACK_WEBHOOK_URL` | ❌ | Slack 배포 알림 (선택사항) | `https://hooks.slack.com/services/...` |

### 2.1 AWS Secrets 등록 방법

**GitHub Repository Settings → Secrets and variables → Actions**

각 Secret 추가:
```
Settings → Secrets and variables → Actions → New repository secret
```

### 2.2 AWS IAM Role (OIDC 연동)

**IAM Role 정책 최소 권한:**

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload"
      ],
      "Resource": "arn:aws:ecr:ap-northeast-2:123456789:repository/last-train-app"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecs:DescribeTaskDefinition",
        "ecs:DescribeServices",
        "ecs:UpdateService",
        "ecs:RegisterTaskDefinition"
      ],
      "Resource": [
        "arn:aws:ecs:ap-northeast-2:123456789:service/last-train-cluster/last-train-service",
        "arn:aws:ecs:ap-northeast-2:123456789:task-definition/last-train-task-def:*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": [
        "arn:aws:iam::123456789:role/ecsTaskExecutionRole",
        "arn:aws:iam::123456789:role/ecsTaskRole"
      ]
    }
  ]
}
```

---

## 3. 파이프라인 실행 조건

**Trigger**: `develop` 브랜치에 push

```bash
# 예: develop 브랜치로 커밋 푸시
git push origin develop

# 또는 Pull Request → develop으로 merge
# (PR merge 후 자동 실행)
```

**자동 실행 시점:**
- ✅ `develop` 브랜치 push
- ✅ `main` → `develop` PR merge
- ❌ 다른 브랜치 push (무시됨)
- ❌ Tag push (무시됨)

---

## 4. 파이프라인 이해하기

### 4.1 Job 의존성

```yaml
backend-test ──┐
              ├─→ docker-build-push ──→ deploy-ecs ──→ notify
frontend-build┘
```

1. `backend-test`와 `frontend-build`가 **병렬** 실행
2. 둘 다 성공해야 `docker-build-push` 시작
3. Docker 빌드 완료 후 `deploy-ecs` 실행
4. 배포 결과 알림

### 4.2 Docker 이미지 태그 전략

**생성되는 태그:**
```
# 타임스탬프 + 짧은 SHA
YYYYMMDD-HHMMSS-<SHORT_SHA>
예: 20260715-143022-a1b2c3d

# 최신 버전
latest
```

**ECR에 저장되는 모습:**
```
123456789.dkr.ecr.ap-northeast-2.amazonaws.com/last-train-app:20260715-143022-a1b2c3d
123456789.dkr.ecr.ap-northeast-2.amazonaws.com/last-train-app:latest
```

---

## 5. ECS Task Definition 구성

파이프라인이 자동으로 Task Definition을 업데이트하기 위해, AWS ECS에 **기본 Task Definition**이 미리 생성되어야 합니다.

### 5.1 Task Definition 템플릿

```json
{
  "family": "last-train-task-def",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "last-train-app",
      "image": "123456789.dkr.ecr.ap-northeast-2.amazonaws.com/last-train-app:latest",
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8080,
          "hostPort": 8080,
          "protocol": "tcp"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/last-train-app",
          "awslogs-region": "ap-northeast-2",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        },
        {
          "name": "TZ",
          "value": "Asia/Seoul"
        }
      ]
    }
  ],
  "executionRoleArn": "arn:aws:iam::123456789:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::123456789:role/ecsTaskRole"
}
```

### 5.2 환경변수 설정

**민감한 정보**는 ECS Task Definition에서 **Secrets Manager** 사용:

```json
"secrets": [
  {
    "name": "JWT_SECRET",
    "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:123456789:secret:last-train/jwt-secret"
  },
  {
    "name": "ODSAY_API_KEY",
    "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:123456789:secret:last-train/odsay-key"
  }
]
```

---

## 6. 배포 후 검증

### 6.1 ECS 서비스 상태 확인

```bash
# ECS 서비스 상태 조회
aws ecs describe-services \
  --cluster last-train-cluster \
  --services last-train-service \
  --region ap-northeast-2 \
  --query 'services[0].[serviceName,status,runningCount,desiredCount]'

# 결과 예시:
# [
#   "last-train-service",
#   "ACTIVE",
#   1,
#   1
# ]
```

### 6.2 ECS 태스크 로그 확인

```bash
# CloudWatch Logs 그룹 확인
aws logs describe-log-streams \
  --log-group-name /ecs/last-train-app \
  --region ap-northeast-2

# 최근 로그 조회
aws logs tail /ecs/last-train-app --follow --region ap-northeast-2
```

### 6.3 배포된 애플리케이션 테스트

```bash
# ECS 서비스의 ALB 주소 확인 (또는 고정 IP)
# AWS Console → ECS → Service → 네트워크 탭에서 확인

curl -X GET https://<ECS_PUBLIC_IP>:8080/actuator/health

# 응답 예시:
# {"status":"UP","components":{"...":"..."}}
```

---

## 7. 배포 실패 처리

### 7.1 일반적인 오류 및 해결책

| 오류 | 원인 | 해결책 |
|------|------|--------|
| `Access Denied to ECR` | AWS 자격증명 문제 | IAM Role ARN 검증, Secrets 확인 |
| `Task Definition not found` | ECS Task Definition 없음 | AWS 콘솔에서 Task Definition 생성 |
| `Service not found` | ECS Service 이름 오류 | ECS_SERVICE_NAME Secret 검증 |
| `Docker build failed` | 빌드 오류 | 로컬에서 `docker build` 테스트 |
| `Frontend build failed` | npm 오류 | `npm run build` 로컬 테스트 |

### 7.2 로그 확인

**GitHub Actions 로그:**
1. 저장소 → Actions 탭
2. 실패한 워크플로우 클릭
3. 각 Job의 로그 확인

**ECS 로그:**
```bash
aws logs tail /ecs/last-train-app --follow
```

### 7.3 롤백 전략

**이전 버전으로 복구:**

```bash
# 이전 이미지 태그 확인
aws ecr describe-images \
  --repository-name last-train-app \
  --region ap-northeast-2 \
  --query 'imageDetails[*].[imageTags,imagePushedAt]' \
  --sort descending

# Task Definition 수정 (이전 이미지 사용)
aws ecs update-service \
  --cluster last-train-cluster \
  --service last-train-service \
  --task-definition last-train-task-def:<REVISION> \
  --force-new-deployment \
  --region ap-northeast-2
```

---

## 8. 성능 최적화

### 8.1 Docker 레이어 캐싱

파이프라인에서 `cache-from` / `cache-to` 사용으로 빌드 시간 단축:

- **의존성 레이어** (Gradle, npm): 변경 없으면 캐시 재사용
- **소스 레이어**: 소스 파일 변경 시만 재빌드

**예상 빌드 시간:**
- 첫 빌드: ~7-8분
- 캐시 히트 시: ~2-3분

### 8.2 Gradle 캐싱

`actions/setup-java@v4`가 자동으로 Gradle 의존성 캐싱:

```yaml
- uses: actions/setup-java@v4
  with:
    java-version: '17'
    distribution: 'eclipse-temurin'
    cache: gradle  # ← 자동 캐싱
```

### 8.3 npm 캐싱

```yaml
- uses: actions/setup-node@v4
  with:
    node-version: '20'
    cache: 'npm'  # ← 자동 캐싱
    cache-dependency-path: 'frontend/package-lock.json'
```

---

## 9. 모니터링 & 알림

### 9.1 Slack 알림

**Slack 웹훅 설정:**

1. Slack 워크스페이스 관리자 로그인
2. App Directory → "Incoming Webhooks" 검색
3. "Add to Slack" → 채널 선택 → "Authorize"
4. Webhook URL 복사
5. GitHub Secrets → `SLACK_WEBHOOK_URL` 저장

**알림 메시지:**
- ✅ 배포 성공: 초록색 배경
- ❌ 배포 실패: 빨간색 배경
- 포함 정보: Repository, Branch, Commit, Author, Docker Image URI

### 9.2 GitHub Actions 상태 뱃지

**README에 뱃지 추가:**

```markdown
[![CI/CD Pipeline](https://github.com/your-org/last-train/actions/workflows/ci-cd.yml/badge.svg?branch=develop)](https://github.com/your-org/last-train/actions/workflows/ci-cd.yml)
```

### 9.3 배포 이력 추적

**GitHub Actions UI:**
- Repository → Actions → CI/CD Pipeline
- 각 워크플로우 실행 이력 확인
- 성공/실패 여부, 소요 시간, Commit 정보

---

## 10. 트러블슈팅

### 10.1 "Access Denied to ECR" 오류

**원인**: AWS 자격증명 문제

**해결책**:
```bash
# 1. IAM Role ARN 확인
aws iam get-role --role-name GitHubActionsRole --query 'Role.Arn'

# 2. GitHub Secrets 업데이트
# Settings → Secrets and variables → Actions
# AWS_ROLE_ARN 값 업데이트

# 3. IAM 정책 확인
aws iam get-role-policy --role-name GitHubActionsRole --policy-name GitHubActionsPolicy
```

### 10.2 "Service update failed" 오류

**원인**: ECS 서비스 또는 Task Definition 오류

**해결책**:
```bash
# 1. ECS 서비스 상태 확인
aws ecs describe-services \
  --cluster last-train-cluster \
  --services last-train-service \
  --region ap-northeast-2

# 2. Task Definition 검증
aws ecs describe-task-definition \
  --task-definition last-train-task-def \
  --region ap-northeast-2

# 3. CloudWatch Events에서 ECS 이벤트 확인
aws events list-rules --region ap-northeast-2
```

### 10.3 "Docker build timeout" 오류

**원인**: 빌드 시간 초과

**해결책**:
- Runner 업그레이드: `runs-on: ubuntu-latest` (기본 2시간)
- 이미지 최적화: 불필요한 레이어 제거
- 캐싱 활성화: `cache-from` 설정 확인

---

## 11. 보안 고려사항

### 11.1 Secret 관리

**GitHub Secrets Best Practices:**
- ✅ 모든 민감 정보를 Secrets에 저장
- ✅ 정기적으로 자격증명 회전
- ✅ 최소 권한 원칙 (IAM Policy)
- ❌ README나 코드에 시크릿 하드코딩 금지

### 11.2 OIDC 연동

**GitHub OIDC의 장점:**
- 장기 액세스 키 불필요
- 각 워크플로우마다 단기 토큰 생성
- AWS의 보안 권장사항 (좋은 사례)

---

## 12. 다음 단계

### 12.1 구현 체크리스트

- [ ] AWS IAM Role (OIDC) 생성
- [ ] GitHub Secrets 등록
- [ ] ECS Cluster & Service 생성
- [ ] ECR Repository 생성
- [ ] ECS Task Definition 생성
- [ ] CloudWatch Logs 그룹 생성
- [ ] Slack 웹훅 설정 (선택사항)

### 12.2 테스트 단계

1. **로컬 테스트**:
   ```bash
   # 백엔드 테스트
   cd backend && ./gradlew test
   
   # 프론트엔드 빌드
   cd frontend && npm run build
   
   # Docker 빌드 (로컬)
   docker build -t last-train-app:local .
   ```

2. **CI/CD 테스트**:
   - develop 브랜치에 작은 커밋 push
   - GitHub Actions 실행 모니터링
   - 각 단계 로그 확인

3. **배포 검증**:
   - ECS 서비스 상태 확인
   - 애플리케이션 health check
   - 로그 확인

---

## 13. 참고 자료

- [GitHub Actions 공식 문서](https://docs.github.com/en/actions)
- [AWS ECS 배포 가이드](https://docs.aws.amazon.com/ecs/)
- [AWS ECR 사용 가이드](https://docs.aws.amazon.com/ecr/)
- [docker/build-push-action](https://github.com/docker/build-push-action)
- [aws-actions/configure-aws-credentials](https://github.com/aws-actions/configure-aws-credentials)

---

**문서 버전**: 1.0  
**마지막 업데이트**: 2026-07-15  
**담당자**: DevOps Team  
**검토 주기**: 분기별
