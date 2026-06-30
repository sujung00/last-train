# 환경변수 설정 가이드

> 막차 알리미 프로젝트의 API Key, DB 비밀번호 등 민감 정보 설정 방법

---

## 1. 왜 환경변수를 쓰나요?

```
# 잘못된 방법 (절대 하지 마세요)
odsay.api-key: lBFKyqVAM...  ← 실제 키가 코드에 포함됨
                                 Git에 올라가면 전 세계에 공개됨

# 올바른 방법
odsay.api-key: ${ODSAY_API_KEY}  ← 환경변수 참조
                                    실제 값은 각 환경(로컬/서버)에서 주입
```

Spring Boot는 `${VARIABLE_NAME}` 형태로 작성된 값을 실행 시점에
OS 환경변수에서 읽어옵니다.

---

## 2. 필요한 환경변수 목록

| 변수명 | 필수 | 설명 | 발급 위치 |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ✅ | `local` 또는 `prod` | 직접 설정 |
| `DB_HOST` | 로컬 기본값 있음 | MySQL 호스트 (`localhost`) | - |
| `DB_PORT` | 로컬 기본값 있음 | MySQL 포트 (`3306`) | - |
| `DB_NAME` | 로컬 기본값 있음 | DB 이름 (`last_train_db`) | - |
| `DB_USERNAME` | ✅ | MySQL 계정명 | 직접 설정 |
| `DB_PASSWORD` | ✅ | MySQL 비밀번호 | 직접 설정 |
| `REDIS_HOST` | 로컬 기본값 있음 | Redis 호스트 (`localhost`) | - |
| `REDIS_PORT` | 로컬 기본값 있음 | Redis 포트 (`6379`) | - |
| `REDIS_PASSWORD` | 운영만 필수 | Redis 비밀번호 | 직접 설정 |
| `JWT_SECRET` | ✅ | JWT 서명 키 (32자 이상) | 직접 생성 |
| `ODSAY_API_KEY` | ✅ | ODsay API 키 | lab.odsay.com |
| `KAKAO_CLIENT_ID` | ✅ | 카카오 앱 키 | developers.kakao.com |
| `KAKAO_CLIENT_SECRET` | ✅ | 카카오 Client Secret | developers.kakao.com |
| `VAPID_PUBLIC_KEY` | ✅ | Web Push 공개 키 | 직접 생성 |
| `VAPID_PRIVATE_KEY` | ✅ | Web Push 비공개 키 | 직접 생성 |
| `CORS_ALLOWED_ORIGINS` | 운영만 필수 | 허용 도메인 (운영) | 직접 설정 |

---

## 3. 로컬 개발 환경 설정 방법

### 방법 1: IntelliJ 실행 설정 (권장)

1. 상단 메뉴 → **Run > Edit Configurations**
2. 사용하는 실행 설정 선택 (예: `LastTrainApplication`)
3. **Environment Variables** 항목 클릭
4. 아래 형식으로 입력:

```
SPRING_PROFILES_ACTIVE=local;DB_USERNAME=root;DB_PASSWORD=your_password;JWT_SECRET=local-dev-secret-min-32-chars-long;ODSAY_API_KEY=your_key;KAKAO_CLIENT_ID=your_id;KAKAO_CLIENT_SECRET=your_secret;VAPID_PUBLIC_KEY=your_pub;VAPID_PRIVATE_KEY=your_priv
```

> 세미콜론(`;`)으로 구분합니다.

---

### 방법 2: .env 파일 + 터미널 (Mac/Linux)

```bash
# 1. .env.example을 복사해서 .env 파일 생성
cp .env.example .env

# 2. .env 파일에 실제 값 입력
vi .env
# 또는
nano .env

# 3. 터미널에서 환경변수 로드
source .env

# 4. 앱 실행
./backend/gradlew bootRun
```

> ⚠️ `.env` 파일은 `.gitignore`에 포함되어 있으므로 Git에 올라가지 않습니다.

---

### 방법 3: 터미널에서 직접 export

```bash
export SPRING_PROFILES_ACTIVE=local
export DB_USERNAME=root
export DB_PASSWORD=your_password
export JWT_SECRET=local-dev-secret-at-least-32-characters
export ODSAY_API_KEY=your_odsay_key

./backend/gradlew bootRun
```

> 터미널을 닫으면 export 값이 사라집니다. 영구 설정은 `~/.zshrc`에 추가하세요.

---

## 4. 운영 서버 설정 방법

### 방법 1: 서버 직접 배포 (systemd)

```bash
# /etc/systemd/system/last-train.service
[Unit]
Description=Last Train API Server

[Service]
ExecStart=/usr/bin/java -jar /app/last-train.jar
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=DB_USERNAME=appuser
Environment=DB_PASSWORD=secure_password
Environment=JWT_SECRET=prod_secret_min_32_chars
# ... 나머지 환경변수
Restart=always

[Install]
WantedBy=multi-user.target
```

### 방법 2: Docker 실행

```bash
docker run -d \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_USERNAME=appuser \
  -e DB_PASSWORD=secure_password \
  -e JWT_SECRET=prod_secret_min_32_chars \
  -e ODSAY_API_KEY=your_key \
  -e KAKAO_CLIENT_ID=your_id \
  -e KAKAO_CLIENT_SECRET=your_secret \
  -e VAPID_PUBLIC_KEY=your_pub \
  -e VAPID_PRIVATE_KEY=your_priv \
  -e CORS_ALLOWED_ORIGINS=https://yourdomain.com \
  -p 8080:8080 \
  last-train:latest
```

### 방법 3: docker-compose

```yaml
# docker-compose.prod.yml
services:
  api:
    image: last-train:latest
    env_file:
      - .env.prod        # 서버에만 존재하는 파일. Git 포함 금지.
    ports:
      - "8080:8080"
```

---

## 5. Spring Boot가 환경변수를 읽는 순서

Spring Boot는 아래 순서로 값을 읽습니다 (앞에 있을수록 우선순위 높음):

```
1. 커맨드라인 인수          java -jar app.jar --jwt.secret=xxx
2. OS 환경변수              export JWT_SECRET=xxx
3. application-{profile}.yml  (profile 활성화 필요)
4. application.yml
```

> 같은 키가 여러 곳에 있으면 우선순위가 높은 값이 사용됩니다.

---

## 6. 키 생성 방법

### JWT_SECRET (32자 이상 무작위 문자열)

```bash
# openssl 사용 (Mac/Linux)
openssl rand -hex 32

# 결과 예시 (실제 사용하지 마세요)
# a3f8b2c1d9e4f7a0b5c6d8e2f1a4b7c0d3e6f9a2b5c8d1e4f7a0b3c6d9e2f5
```

### VAPID 키 쌍 (Web Push)

```bash
# Node.js 필요
npx web-push generate-vapid-keys

# 결과 예시
# Public Key:  BExample...
# Private Key: ABC123...
```

---

## 7. 체크리스트

로컬 개발 시작 전:
- [ ] `SPRING_PROFILES_ACTIVE=local` 설정
- [ ] `DB_USERNAME`, `DB_PASSWORD` 설정
- [ ] `JWT_SECRET` 32자 이상으로 설정
- [ ] `ODSAY_API_KEY` 설정 (ODsay Server 플랫폼 키)
- [ ] MySQL 실행 확인 (`last_train_db` 데이터베이스 생성)
- [ ] Redis 실행 확인

운영 배포 전:
- [ ] `SPRING_PROFILES_ACTIVE=prod` 설정
- [ ] 모든 환경변수 설정 완료 확인
- [ ] `REDIS_PASSWORD` 설정
- [ ] `CORS_ALLOWED_ORIGINS` 운영 도메인으로 변경
- [ ] Swagger UI 비활성화 확인 (`/swagger-ui` 접근 불가 확인)
- [ ] ODsay Server 플랫폼에 운영 서버 IP 등록
- [ ] 카카오 developers에 운영 `redirect_uri` 등록