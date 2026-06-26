# 기술 스택 (Tech Stack)

> 업데이트: 2026-06-26
> 프로젝트: 막차 알리미 (Last Train Notifier)
> 상태: Active Development

---

## 1. 백엔드 스택 (Backend)

### 1.1 프레임워크 & 런타임

| 항목 | 버전 | 용도 |
|------|------|------|
| **Spring Boot** | 3.2.4 | REST API 프레임워크 |
| **Spring Framework** | (Boot 3.2.4 포함) | DI, AOP, 설정 관리 |
| **Java** | 17 | 프로그래밍 언어 |
| **Gradle** | (wrapper) | 빌드 도구 |

### 1.2 데이터 계층

| 항목 | 버전 | 용도 |
|------|------|------|
| **Spring Data JPA** | (Boot 포함) | ORM, 데이터 접근 계층 |
| **Hibernate** | (JPA 구현체) | 객체-관계 매핑 |
| **MySQL** | 8.0 | 주 데이터베이스 (회원, 즐겨찾기, 알림) |
| **Flyway** | (최신) | 데이터베이스 마이그레이션 |
| **Redis** | 7.0 | 캐시, Refresh Token, Delay Queue |

### 1.3 보안 & 인증

| 항목 | 버전 | 용도 |
|------|------|------|
| **Spring Security** | (Boot 포함) | 인증/인가, CORS |
| **JJWT** | 0.12.5 | JWT 토큰 생성/검증 |
| **Bouncy Castle** | 1.70 | 암호화 (Web Push VAPID) |

### 1.4 API & 문서화

| 항목 | 버전 | 용도 |
|------|------|------|
| **Springdoc-OpenAPI** | 2.3.0 | OpenAPI 3.0 자동 생성 (Swagger UI) |

### 1.5 알림 & 푸시

| 항목 | 버전 | 용도 |
|------|------|------|
| **Web Push Library** | 5.1.1 (Martijn) | VAPID 기반 브라우저 푸시 알림 |

### 1.6 유틸리티

| 항목 | 버전 | 용도 |
|------|------|------|
| **Lombok** | (최신) | 보일러플레이트 코드 감소 (@Getter, @Setter, @Log) |

### 1.7 테스트

| 항목 | 버전 | 용도 |
|------|------|------|
| **JUnit 5** | (Spring Boot 포함) | 단위/통합 테스트 |
| **Spring Boot Test** | 3.2.4 | Spring 테스트 유틸리티 |
| **Spring Security Test** | (Boot 포함) | 보안 테스트 |
| **TestContainers** | 1.20.6 | MySQL 컨테이너 기반 통합 테스트 |
| **docker-java** | 3.3.6 | Docker 자동화 |

---

## 2. 프론트엔드 스택 (Frontend)

### 2.1 코어 프레임워크

| 항목 | 버전 | 용도 |
|------|------|------|
| **React** | 19.2.6 | UI 라이브러리 |
| **React DOM** | 19.2.6 | DOM 렌더링 |
| **React Router DOM** | 7.18.0 | 라우팅 |

### 2.2 빌드 & 개발 도구

| 항목 | 버전 | 용도 |
|------|------|------|
| **Vite** | 8.0.12 | 번들러 및 개발 서버 |
| **@vitejs/plugin-react** | 6.0.1 | React 플러그인 |
| **Tailwind CSS** | 4.3.1 | 유틸리티 CSS |
| **@tailwindcss/vite** | 4.3.1 | Tailwind Vite 플러그인 |

### 2.3 HTTP & 상태 관리

| 항목 | 버전 | 용도 |
|------|------|------|
| **Axios** | 1.18.0 | HTTP 클라이언트 |
| **React Context API** | (내장) | 전역 상태 관리 (예정) |
| **React Hooks** | (내장) | useState, useEffect, useRef 등 |

### 2.4 린팅 & 타입

| 항목 | 버전 | 용도 |
|------|------|------|
| **ESLint** | 10.3.0 | 코드 린팅 |
| **@eslint/js** | 10.0.1 | ESLint 설정 |
| **eslint-plugin-react-hooks** | 7.1.1 | React Hooks 린트 규칙 |
| **eslint-plugin-react-refresh** | 0.5.2 | Fast Refresh 린트 규칙 |
| **TypeScript** | (devDep) | 타입 체크 (선택적) |

### 2.5 개발 환경 설정

| 항목 | 버전 | 용도 |
|------|------|------|
| **globals** | 17.6.0 | 글로벌 변수 설정 |

---

## 3. 외부 API 연동

### 3.1 경로 & 대중교통

| API | 버전 | 용도 | 인증 |
|-----|------|------|------|
| **ODsay API** | v1 | 지하철/버스 경로 탐색, 막차 시간표 | Server 플랫폼 (IP 기반) |
| **서울시 버스 API** | REST | 버스 도착 정보 (실시간) | API Key |
| **경기도 버스 API** | v2 | 버스 노선 정보 (요일별 막차) | API Key |

### 3.2 사용자 인증

| API | 버전 | 용도 | 방식 |
|-----|------|------|------|
| **카카오 로그인** | OAuth2.0 | 소셜 로그인 | Authorization Code Flow |
| **카카오 사용자 정보** | v2 | 사용자 이메일/닉네임 조회 | Bearer Token |

### 3.3 위치 (향후)

| API | 버전 | 용도 | 방식 |
|-----|------|------|------|
| **카카오 로컬** | v2 (예정) | 장소 검색 (프론트 직접 호출) | JavaScript SDK |

---

## 4. 인프라 & 배포

### 4.1 컨테이너화

| 항목 | 버전 | 용도 |
|------|------|------|
| **Docker** | (최신) | 컨테이너 이미지 |
| **Docker Compose** | 3.8 | 로컬 개발 환경 (MySQL, Redis 오케스트레이션) |

### 4.2 환경 관리

| 항목 | 방식 | 용도 |
|------|------|------|
| **.env 파일** | 환경변수 주입 | 민감 정보 (API Key, 비밀번호) |
| **application.yml** | Spring 설정 | 공통 설정 |
| **application-local.yml** | Spring 프로필 | 로컬 개발 오버라이드 |
| **application-prod.yml** | Spring 프로필 | 운영 환경 설정 (예정) |

---

## 5. 개발 환경 스펙

### 5.1 개발 머신

- **OS**: macOS (Apple Silicon 지원)
- **Java**: OpenJDK 17 (GraalVM 호환)
- **IDE**: IntelliJ IDEA / VS Code
- **패키지 매니저**: Gradle (Java), npm (Node.js)

### 5.2 로컬 개발 구성

```
Host Machine (macOS)
├─ IDE (IntelliJ / VS Code)
├─ Gradle Wrapper
├─ npm / Node.js
└─ Docker Desktop
   └─ Docker Compose
      ├─ MySQL 8.0
      ├─ Redis 7.0
      └─ Spring Boot App (선택적 컨테이너 실행)
```

### 5.3 포트 맵핑

| 서비스 | 로컬 포트 | 컨테이너 포트 | 환경변수 |
|--------|-----------|---------------|---------|
| Spring Boot | 8080 | 8080 | - |
| React (Vite) | 3000 | - | (vite.config.js) |
| MySQL | 3307 | 3306 | DB_PORT=3307 |
| Redis | 6380 | 6379 | REDIS_PORT=6380 |

---

## 6. 주요 기술 선택 근거

### 6.1 Spring Boot 3.2.4

- **이유**: 최신 LTS, Java 17 지원, 빠른 개발 속도
- **대안 미검토**: 프로젝트 시작 당시 표준

### 6.2 JPA + Hibernate

- **이유**: 테이블 4개, 관계 단순 → ORM 오버헤드 미미
- **대안**: QueryDSL (복잡 조인 필요 시 향후 도입)

### 6.3 Redis (단순 Delay Queue)

- **이유**: 알림 일 수백 건 → Kafka 오버엔지니어링
- **패턴**: Redis ZSET + Lua Script + @Scheduled
- **향후 확장**: NotificationSender 인터페이스로 Kafka 교체 가능

### 6.4 서울시/경기도 버스 API 추가 (변경)

- **이유**: ODsay는 경기도 버스 정적 시간표만 제공 → 실시간 정보 필요
- **구현**: SeoulBusArrivalClient (서울), GyeonggiBusRouteClient (경기)
- **주의**: API 호출 횟수 증가 → 캐싱 전략 필수

### 6.5 React + Vite

- **이유**: 빠른 개발 루프, 현대적 번들러, 최소 설정
- **상태 관리**: Context API + useState (React Query 미도입)

### 6.6 Tailwind CSS 4.3.1

- **이유**: 유틸리티 기반, 빠른 프로토타입, 커스텀 색상 용이
- **대안**: styled-components (필요 시 향후 도입)

---

## 7. 보안 설정

### 7.1 인증

- **방식**: JWT (Access Token + Refresh Token)
- **Access Token TTL**: 30분 (1800000ms)
- **Refresh Token TTL**: 7일 (604800000ms)
- **저장소**: Refresh Token → Redis (자동 만료)

### 7.2 CORS

- **허용 출처**: `http://localhost:3000` (로컬), 운영 환경에서 동적 설정
- **구성**: SecurityConfig + application-local.yml

### 7.3 API Key 관리

- **민감 정보**: .env 파일 (.gitignore 포함)
- **환경변수 예시**:
  ```
  JWT_SECRET=<32자 이상 무작위>
  ODSAY_API_KEY=<Server 플랫폼 키>
  SEOUL_BUS_API_KEY=<서울시 API 키>
  GYEONGGI_BUS_API_KEY=<경기도 API 키>
  KAKAO_CLIENT_ID=<카카오 앱 키>
  KAKAO_CLIENT_SECRET=<카카오 비밀키>
  ```

---

## 8. 성능 최적화

### 8.1 캐싱 전략

| 데이터 | 저장소 | TTL | 갱신 방식 |
|--------|--------|-----|----------|
| Refresh Token | Redis | 7일 | 자동 만료 |
| ODsay 응답 | Redis | 1시간 (설정 가능) | 수동 무효화 |
| 버스 정보 | 미캐싱 | - | 실시간 조회 |

### 8.2 데이터베이스

- **인덱싱**: User.email (UNIQUE), Favorite.userId + stationId
- **조인**: user ← favorite, user ← subscription ← schedule (간단함)
- **N+1 문제**: @EntityGraph 또는 fetch join (필요시 적용)

### 8.3 API 호출

- **ODsay**: 일 3,000 호출 제한 → 캐싱 필수
- **서울시/경기도 버스**: 별도 제한 없음 (캐싱 선택)

---

## 9. 테스트 전략

### 9.1 단위 테스트

- **프레임워크**: JUnit 5
- **커버리지**: Service, Repository 계층 우선
- **도구**: Mockito (의존성 mock)

### 9.2 통합 테스트

- **도구**: TestContainers + MySQL
- **범위**: Repository, 트랜잭션 동작 검증
- **설정**: application-test.yml (별도 DB)

### 9.3 E2E 테스트 (향후)

- **도구**: Playwright (React 컴포넌트 테스트)
- **범위**: 로그인 → 검색 → 알림 구독 시나리오

---

## 10. 운영 환경 (Production)

### 10.1 배포 (예정)

| 항목 | 도구 | 상태 |
|------|------|------|
| **컨테이너 이미지** | Docker | 준비 중 |
| **오케스트레이션** | Kubernetes (선택) | 미정 |
| **CI/CD** | GitHub Actions (예정) | 미구현 |

### 10.2 모니터링 (예정)

- **로그**: Spring Cloud Sleuth + ELK Stack (선택)
- **메트릭**: Micrometer + Prometheus (선택)
- **트레이싱**: Jaeger (선택)

### 10.3 설정 다변화

- **application-prod.yml**: 운영 환경 설정 분리
- **환경변수**: AWS Secrets Manager 또는 Spring Cloud Config

---

## 11. 마이그레이션 & 업그레이드 경로

### 11.1 단기 (현재 ~ 3개월)

- ✅ 기본 CRUD API 완성
- ✅ 인증 (JWT + Kakao OAuth2)
- ✅ 외부 API 통합 (ODsay, 버스 API)
- 🔜 알림 구독 & 스케줄링
- 🔜 프론트엔드 UI 완성

### 11.2 중기 (3 ~ 6개월)

- 🔜 통합 테스트 100% 커버리지
- 🔜 성능 최적화 (인덱싱, 캐싱 정책)
- 🔜 Docker 컨테이너 배포
- 🔜 CI/CD 파이프라인

### 11.3 장기 (6개월 이상)

- 🔜 마이크로서비스 분리 (향후 필요시)
- 🔜 Kafka 도입 (알림 량 증가 시)
- 🔜 GraphQL API (선택)
- 🔜 모바일 앱 (React Native)

---

## 12. 의존성 보안 관리

### 12.1 정기 업데이트

- **Gradle**: `gradle dependencyUpdates` 월 1회
- **npm**: `npm outdated` 월 1회
- **취약점 스캔**: GitHub Dependabot 활성화

### 12.2 보안 정책

- ❌ 개발 환경: 최신 마이너 버전 + 보안 패치
- ❌ 운영 환경: LTS/안정 버전만 사용
- ❌ 주요 버전 업: 철저한 테스트 필수

---

## 13. 참고 자료

- [Spring Boot 3.2.4 공식 문서](https://spring.io/projects/spring-boot)
- [React 19 문서](https://react.dev)
- [Vite 8 가이드](https://vitejs.dev)
- [Tailwind CSS 4 문서](https://tailwindcss.com)
- [JJWT 문서](https://github.com/jpadilla/pyjwt)
- [ODsay API 문서](https://lab.odsay.com)

---

**마지막 업데이트**: 2026-06-26  
**담당**: Backend + Frontend Team  
**리뷰**: 필요시 월 1회
