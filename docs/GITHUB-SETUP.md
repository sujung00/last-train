# GitHub Secrets & CI/CD 설정 가이드

> **이슈**: #3  
> **날짜**: 2026-07-15  
> **상태**: Ready for Implementation

---

## 1단계: AWS IAM Role 생성 (OIDC)

GitHub Actions가 AWS에 접근하려면 **IAM Role**이 필요합니다. OIDC 방식을 사용해 장기 액세스 키 없이 안전하게 인증합니다.

### 1.1 OIDC Provider 생성

**AWS Console → IAM → Identity providers**

```bash
# CLI를 통한 생성
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

### 1.2 IAM Role 생성

**Console 경로**: IAM → Roles → Create role

**신뢰 정책 (Trust Policy):**

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::YOUR_AWS_ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:YOUR_GITHUB_ORG/last-train:ref:refs/heads/develop"
        }
      }
    }
  ]
}
```

**주의**: 다음 부분을 실제 값으로 변경
- `YOUR_AWS_ACCOUNT_ID`: 12자리 AWS 계정 ID
- `YOUR_GITHUB_ORG`: GitHub Organization (예: `anthropics`)
- `last-train`: 저장소 이름

### 1.3 IAM Policy 생성

**Console 경로**: IAM → Policies → Create policy

**정책 이름**: `GitHubActionsLastTrainPolicy`

**정책 JSON**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ECRAccess",
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:DescribeRepositories",
        "ecr:DescribeImages",
        "ecr:ListImages"
      ],
      "Resource": [
        "arn:aws:ecr:ap-northeast-2:YOUR_AWS_ACCOUNT_ID:repository/last-train-app"
      ]
    },
    {
      "Sid": "ECSAccess",
      "Effect": "Allow",
      "Action": [
        "ecs:DescribeTaskDefinition",
        "ecs:DescribeServices",
        "ecs:UpdateService",
        "ecs:RegisterTaskDefinition",
        "ecs:ListTaskDefinitions"
      ],
      "Resource": [
        "arn:aws:ecs:ap-northeast-2:YOUR_AWS_ACCOUNT_ID:service/last-train-cluster/last-train-service",
        "arn:aws:ecs:ap-northeast-2:YOUR_AWS_ACCOUNT_ID:task-definition/last-train-task-def:*"
      ]
    },
    {
      "Sid": "IAMPassRole",
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": [
        "arn:aws:iam::YOUR_AWS_ACCOUNT_ID:role/ecsTaskExecutionRole",
        "arn:aws:iam::YOUR_AWS_ACCOUNT_ID:role/ecsTaskRole"
      ]
    }
  ]
}
```

### 1.4 Policy를 Role에 연결

1. **AWS Console** → **IAM** → **Roles** → `GitHubActionsRole` (생성한 Role)
2. **Add permissions** → **Attach policies**
3. `GitHubActionsLastTrainPolicy` 선택

### 1.5 Role ARN 확인

```bash
aws iam get-role --role-name GitHubActionsRole --query 'Role.Arn' --output text
```

**출력 예시**:
```
arn:aws:iam::123456789012:role/GitHubActionsRole
```

---

## 2단계: ECR Repository 생성

### 2.1 ECR Repository 생성

```bash
aws ecr create-repository \
  --repository-name last-train-app \
  --region ap-northeast-2 \
  --image-tag-mutability MUTABLE \
  --image-scanning-configuration scanOnPush=true
```

### 2.2 ECR Registry URI 확인

```bash
# ECR Registry URI 조회
aws ecr describe-repositories \
  --repository-names last-train-app \
  --region ap-northeast-2 \
  --query 'repositories[0].repositoryUri' \
  --output text
```

**출력 예시**:
```
123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/last-train-app
```

---

## 3단계: AWS Secrets Manager 설정

### 3.1 민감 정보 저장

```bash
# JWT Secret
aws secretsmanager create-secret \
  --name last-train/jwt-secret \
  --secret-string "your-super-secret-jwt-key-32-chars-min" \
  --region ap-northeast-2

# ODSAY API Key
aws secretsmanager create-secret \
  --name last-train/odsay-key \
  --secret-string "your-odsay-api-key" \
  --region ap-northeast-2

# 기타 API Keys
aws secretsmanager create-secret \
  --name last-train/seoul-bus-key \
  --secret-string "your-seoul-bus-api-key" \
  --region ap-northeast-2

aws secretsmanager create-secret \
  --name last-train/gyeonggi-bus-key \
  --secret-string "your-gyeonggi-bus-api-key" \
  --region ap-northeast-2
```

---

## 4단계: ECS Cluster & Service 생성

### 4.1 ECS Cluster 생성

```bash
aws ecs create-cluster \
  --cluster-name last-train-cluster \
  --region ap-northeast-2
```

### 4.2 CloudWatch Logs 그룹 생성

```bash
aws logs create-log-group \
  --log-group-name /ecs/last-train-app \
  --region ap-northeast-2

# 로그 retention 정책 설정 (30일)
aws logs put-retention-policy \
  --log-group-name /ecs/last-train-app \
  --retention-in-days 30 \
  --region ap-northeast-2
```

### 4.3 ECS Task Execution Role 생성

```bash
# 신뢰 정책
cat > trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

# Role 생성
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document file://trust-policy.json

# AWS 관리형 정책 연결
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy

# Secrets Manager 접근 권한 추가
cat > secrets-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:ap-northeast-2:YOUR_AWS_ACCOUNT_ID:secret:last-train/*"
    }
  ]
}
EOF

aws iam put-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-name AllowSecretsAccess \
  --policy-document file://secrets-policy.json
```

### 4.4 ECS Task Role 생성

```bash
# Task Role (애플리케이션 실행 권한)
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document file://trust-policy.json

# 필요한 권한 추가 (데이터베이스, 캐시 등)
# 현재는 최소 권한으로 설정
```

### 4.5 ECS Task Definition 생성

```bash
cat > task-definition.json <<EOF
{
  "family": "last-train-task-def",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "last-train-app",
      "image": "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/last-train-app:latest",
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
      ],
      "secrets": [
        {
          "name": "JWT_SECRET",
          "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:last-train/jwt-secret"
        },
        {
          "name": "ODSAY_API_KEY",
          "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:last-train/odsay-key"
        },
        {
          "name": "SEOUL_BUS_API_KEY",
          "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:last-train/seoul-bus-key"
        },
        {
          "name": "GYEONGGI_BUS_API_KEY",
          "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:last-train/gyeonggi-bus-key"
        }
      ]
    }
  ],
  "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::123456789012:role/ecsTaskRole"
}
EOF

# Task Definition 등록
aws ecs register-task-definition \
  --cli-input-json file://task-definition.json \
  --region ap-northeast-2
```

### 4.6 ECS Service 생성

**VPC & Subnets 준비 필수!**

```bash
# 먼저 VPC 및 서브넷 ID 확인
aws ec2 describe-vpcs \
  --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' \
  --output text

aws ec2 describe-subnets \
  --filters Name=vpc-id,Values=vpc-xxxxx \
  --query 'Subnets[*].[SubnetId,AvailabilityZone]' \
  --output table

# Service 생성
aws ecs create-service \
  --cluster last-train-cluster \
  --service-name last-train-service \
  --task-definition last-train-task-def:1 \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxxxx],securityGroups=[sg-xxxxx],assignPublicIp=ENABLED}" \
  --region ap-northeast-2
```

---

## 5단계: GitHub Secrets 등록

### 5.1 GitHub Repository Settings

**경로**: Repository → Settings → Secrets and variables → Actions

### 5.2 Repository Secrets 추가

각 Secret을 다음과 같이 등록:

| Secret Name | Value |
|------------|-------|
| `AWS_REGION` | `ap-northeast-2` |
| `AWS_ROLE_ARN` | `arn:aws:iam::123456789012:role/GitHubActionsRole` |
| `ECR_REGISTRY` | `123456789012.dkr.ecr.ap-northeast-2.amazonaws.com` |
| `ECS_CLUSTER_NAME` | `last-train-cluster` |
| `ECS_SERVICE_NAME` | `last-train-service` |
| `ECS_TASK_DEFINITION` | `last-train-task-def` |
| `ECS_CONTAINER_NAME` | `last-train-app` |

### 5.3 Secrets 추가 방법

1. **GitHub Repository** → **Settings** 탭
2. **Secrets and variables** → **Actions** 클릭
3. **New repository secret** 버튼 클릭
4. **Name**: `AWS_REGION`
5. **Secret**: `ap-northeast-2`
6. **Add secret** 클릭

**반복**: 위 표의 모든 Secrets 등록

### 5.4 선택사항: Slack 알림

Slack 웹훅 URL도 등록할 수 있습니다:

```
Name: SLACK_WEBHOOK_URL
Secret: https://hooks.slack.com/services/YOUR/WEBHOOK/URL
```

---

## 6단계: 검증

### 6.1 IAM Role 권한 확인

```bash
# Role의 정책 확인
aws iam list-attached-role-policies \
  --role-name GitHubActionsRole \
  --region ap-northeast-2

# 정책 상세 확인
aws iam get-role-policy \
  --role-name GitHubActionsRole \
  --policy-name GitHubActionsLastTrainPolicy \
  --region ap-northeast-2
```

### 6.2 ECR Repository 확인

```bash
aws ecr describe-repositories \
  --repository-names last-train-app \
  --region ap-northeast-2
```

### 6.3 ECS Cluster 확인

```bash
# Cluster 상태
aws ecs describe-clusters \
  --clusters last-train-cluster \
  --region ap-northeast-2

# Service 상태
aws ecs describe-services \
  --cluster last-train-cluster \
  --services last-train-service \
  --region ap-northeast-2
```

### 6.4 GitHub Secrets 확인

**GitHub Repository** → **Settings** → **Secrets and variables** → **Actions**

모든 Secret이 목록에 표시되는지 확인

---

## 7단계: 첫 배포 테스트

### 7.1 로컬에서 테스트

```bash
# 1. 백엔드 테스트
cd backend && ./gradlew test

# 2. 프론트엔드 빌드
cd frontend && npm run build

# 3. Docker 로컬 빌드
docker build -t last-train-app:local .

# 4. Docker 실행 (로컬 테스트)
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e JWT_SECRET=test-secret \
  last-train-app:local
```

### 7.2 CI/CD 테스트

```bash
# develop 브랜치에 작은 변경 push
git checkout develop
echo "# Test deployment" >> README.md
git add README.md
git commit -m "test: trigger CI/CD pipeline"
git push origin develop
```

### 7.3 배포 모니터링

1. **GitHub** → **Repository** → **Actions** 탭
2. 방금 푸시한 커밋의 워크플로우 실행 확인
3. 각 Job 진행 상황 모니터링:
   - ✅ Backend Tests
   - ✅ Frontend Build
   - ✅ Docker Build & Push
   - ✅ ECS Deployment

### 7.4 배포 확인

```bash
# ECS Service 상태 확인
aws ecs describe-services \
  --cluster last-train-cluster \
  --services last-train-service \
  --region ap-northeast-2 \
  --query 'services[0].[serviceName,status,runningCount,desiredCount]'

# 로그 확인
aws logs tail /ecs/last-train-app --follow
```

---

## 8단계: 문제 해결

### 8.1 "Access Denied" 오류

```bash
# 1. Role 신뢰 정책 확인
aws iam get-role --role-name GitHubActionsRole

# 2. OIDC Provider 확인
aws iam list-open-id-connect-providers

# 3. Secrets 재확인
# GitHub Settings → Secrets
```

### 8.2 "ECR Repository not found"

```bash
# ECR Repository 재생성
aws ecr create-repository \
  --repository-name last-train-app \
  --region ap-northeast-2
```

### 8.3 "ECS Service not found"

```bash
# ECS Cluster 및 Service 상태 확인
aws ecs list-services \
  --cluster last-train-cluster \
  --region ap-northeast-2

# Service 재생성 필요 시
aws ecs create-service \
  --cluster last-train-cluster \
  --service-name last-train-service \
  --task-definition last-train-task-def:1 \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxxxx],assignPublicIp=ENABLED}" \
  --region ap-northeast-2
```

---

## 체크리스트

### AWS 설정
- [ ] OIDC Provider 생성
- [ ] IAM Role (GitHubActionsRole) 생성
- [ ] IAM Policy (GitHubActionsLastTrainPolicy) 생성
- [ ] ECR Repository 생성
- [ ] Secrets Manager에 민감 정보 저장
- [ ] ECS Cluster 생성
- [ ] CloudWatch Logs 그룹 생성
- [ ] ECS Task Execution Role 생성
- [ ] ECS Task Role 생성
- [ ] ECS Task Definition 등록
- [ ] ECS Service 생성

### GitHub 설정
- [ ] GitHub Secrets 등록 (AWS_REGION, AWS_ROLE_ARN, ECR_REGISTRY 등)
- [ ] Slack 웹훅 (선택사항)

### 테스트
- [ ] 로컬 빌드 및 테스트
- [ ] CI/CD 워크플로우 실행
- [ ] 배포 확인
- [ ] 애플리케이션 health check

---

**완료 후**: 메인 개발팀에 배포 가능 상태 공지
