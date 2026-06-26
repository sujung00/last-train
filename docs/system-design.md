# system-design.md

| 항목    | 내용                              |
|---------|-----------------------------------|
| Project | 막차 알리미 (Last Train Notifier) |
| Author  | Reviewed by Senior Backend Architect |
| Version | 2.0                               |
| Date    | 2026-05                           |

---

# 1. Architecture Overview

## 1.1 서비스 한 줄 정의

서울 + 경기도권 대중교통(지하철 + 버스)을 이용하는 사용자가
목적지까지 도착할 수 있는 마지막 경로와 출발 마감 시각을 안내하는 웹 서비스.

## 1.2 시스템 구성도

```
[Browser (React SPA)]
  │  Kakao Local API (장소 검색, 직접 호출)
  │  HTTPS / REST (/api/v1/*)
  ▼
[Spring Boot API Server]
  │
  ├── [MySQL]             - 회원, 즐겨찾기, 알림 구독/예약
  ├── [Redis]             - Refresh Token, 응답 캐싱, Delay Queue
  ├── [ODsay API]         - 경로 탐색 + 지하철/버스 막차 시간표 (Server 플랫폼, IP 인증)
  ├── [서울시 버스 API]   - 버스 도착 정보 (실시간, REST)
  ├── [경기도 버스 API]   - 버스 노선 정보 (요일별 막차, REST)
  └── [Kakao API]         - OAuth2.0 (백엔드에서만 호출)

[Redis Delay Queue Worker - 1초]
  └── notification:queue (ZSET) polling → Web Push (VAPID) 발송
```

## 1.3 아키텍처 스타일 조합

| 계층     | 스타일                          | 선택 이유                                                  |
|----------|---------------------------------|------------------------------------------------------------|
| Backend  | Layered Architecture            | Controller→Service→Repository 계층 강제.                  |
|          |                                 | AI 코드 생성 시 로직 혼입 방지. 테스트 경계 명확.          |
| Backend  | API-First Design                | 프론트·백엔드 독립 개발. JSON 계약 선행 정의.              |
| Backend  | Component-Based Modular         | 기능 단위 패키지(auth/route/favorite/notification).        |
|          |                                 | 기능 추가 시 영향 범위 최소화.                             |
| Backend  | Lightweight Event-Driven        | 알림 발송을 요청 스레드에서 분리.                          |
|          |                                 | Kafka 없이 @Scheduled + Redis Delay Queue로 충분한 규모.   |
| Frontend | Component-Based UI Architecture | 화면 단위 재사용 컴포넌트 분리.                            |
|          |                                 | 막차 결과 카드, 타이머 등 독립적 UI 단위 관리.             |

## 1.4 아키텍처 선택 근거 (10개 기준)

| #  | 아키텍처                                       | 적용 여부          | 근거 (실제 파일/코드 기준)                                                                                                                                                                                       |
|----|------------------------------------------------|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | 3-Tier / Layered Architecture                  | ✅ 적용            | Controller → Service → Repository → Domain 4계층 분리 확인. AuthController → AuthService → UserRepository → User, FavoriteController → FavoriteService → FavoriteRepository → Favorite. global/ 패키지가 공통 인프라 계층 담당. |
| 2  | Feature-Sliced / Vertical Slice Architecture   | ✅ 적용            | 기능 단위 패키지 구조 확인: com.lasttrain.auth/, favorite/, route/, notification/. 각 패키지 내에 controller/service/repository/domain/dto 전 계층이 수직 포함됨. Layered Architecture와 혼합 구조.             |
| 3  | Component-Based UI Architecture                | 🔜 적용 예정       | 백엔드 Java 코드만 존재. React 컴포넌트 파일 없음. system-design.md 4절에 폴더 구조·컴포넌트 분리 원칙 전체가 설계로 기술됨.                                                                                    |
| 4  | State Management Architecture                  | 🔜 적용 예정       | 백엔드 Java 코드만 존재. AuthContext, useState 등 프론트 상태관리 코드 없음. system-design.md 4.4절, 17절에 설계만 기술됨.                                                                                       |
| 5  | API-First / Client-Server Architecture         | ✅ 적용            | SwaggerConfig.java에서 OpenAPI 3.0 명세 정의. 전체 컨트롤러에 @Tag, @Operation, @ApiResponses, @SecurityRequirements 어노테이션 적용. ApiResponse<T> 공통 응답 포맷(global/response/ApiResponse.java). springdoc-openapi-starter-webmvc-ui:2.3.0 의존성 확인. |
| 6  | Data Model / Repository Architecture           | ✅ 적용            | UserRepository, FavoriteRepository가 JpaRepository<T, Long> 상속. User.java (@Table, @UniqueConstraint, @Index), Favorite.java (@Table, @Index, DECIMAL(10,7) 정밀도 지정) JPA 엔티티 설계. Spring Data JPA 메서드 네이밍 쿼리 활용. |
| 7  | Clean / Hexagonal Architecture                 | ❌ 미적용          | 포트(인터페이스)·어댑터 분리 없음. User.java, Favorite.java 도메인 엔티티가 @Entity, @EntityListeners, @CreatedDate 등 Spring 어노테이션에 직접 의존. OdsayClient 인터페이스는 system-design.md 3.4절 설계로만 존재, 실제 코드 없음. |
| 8  | Modular Monolith → Microservices Architecture  | ❌ 미적용          | LastTrainApplication.java 단일 진입점. 서비스 간 HTTP 통신·독립 배포 구조 없음. 기능별 패키지 분리는 있으나 독립 배포 가능한 모듈이 아님.                                                                       |
| 9  | Event-Driven / Queue / CQRS Architecture       | ✅ 적용            | Redis ZSET Delay Queue 구현 확인: NotificationQueueService.java (enqueue/popDue/cancel), NotificationScheduler.java (@Scheduled fixedDelay=1초). Lua script로 ZRANGEBYSCORE + ZREM 원자적 처리. CQRS(읽기·쓰기 모델 분리)는 없음. Spring 이벤트(UserSignedUpEvent 등)는 system-design.md 12절 설계만 존재, 실제 코드 없음. |
| 10 | AI-Native RAG / Agent Architecture             | ❌ 미적용          | AI·LLM·임베딩·벡터 DB 관련 코드 없음. 전체 Java 소스 파일 어디에도 AI 라이브러리 의존성 없음.                                                                                                                  |

## 1.5 의도적으로 단순화한 부분

- 시간표 로컬 DB 저장 없음 (ODsay 실시간 호출 + Redis 캐싱)
- 알림 재시도 로직 없음 (MVP 범위 외)
- 이메일 인증 없음 (가입 즉시 사용)
- 다중 인스턴스 배포 없음 (단일 인스턴스 가정)
- Atomic Design 미적용 (pages/components 2단계 구조로 충분)
- 전역 상태관리 라이브러리 최소화 (Context API로 충분)
- React Query 미도입 (useState + useEffect로 충분한 조회 패턴)

---

# 2. Architecture Decisions

## 2.1 Spring Data JPA 선택

- 테이블 4개, 관계 단순 (user ← favorite, user ← subscription ← schedule)
- 복잡한 조인 쿼리 없음 → ORM 오버헤드 미미
- MyBatis 대비 객체 지향 설계 + 직접 경험 목적
- 복잡 조회 필요 시 JPQL로 충분. QueryDSL은 MVP 범위 외.

## 2.2 외부 대중교통 API 전략

### 2.2.1 ODsay API (주요 경로 탐색)

- **searchSubwaySchedule** (firstLastFlag=2) → 지하철 막차
- **searchBusLane** (busLastTime) → 버스 막차 (정적 시간표, 서울/경기도 포함)
- **특징**: Server 플랫폼 (IP 기반 인증), 캐싱 필수 (일 3,000 호출 제한)

> **주의**: busLastTime은 정적 시간표 기반.
> 실제 버스 결행/지연은 반영 안 됨 → 결과 화면 안내 문구 필수.

### 2.2.2 서울시/경기도 버스 API (실시간 보충)

**추가 연동 이유:**
- ODsay의 버스 시간표는 "정적" 기반 → 실제 막차와 차이 가능성
- 더 정확한 요일별 막차 시간 필요 → 별도 API 연동

**구현:**
- **SeoulBusArrivalClient**: 서울시 버스 도착 정보 (실시간, REST)
  - Endpoint: http://ws.bus.go.kr/api/rest/arrive/getArrInfoByRouteList
  - 파라미터: stId (정류소), busRouteId (노선), ord (버스 순번)
  - 응답: XML → lastTm (yyyyMMddHHmmss) 파싱

- **GyeonggiBusRouteClient**: 경기도 버스 노선 정보 (요일별 막차)
  - Endpoint: https://apis.data.go.kr/6410000/busrouteservice/v2/getBusRouteInfoItemv2
  - 파라미터: routeId (노선 ID), format=json
  - 응답: JSON → 요일별 필드 (upLastTime, satUpLastTime, sunUpLastTime)
  - 로직: LocalDate.now().getDayOfWeek()로 오늘 요일 판단 → 해당 필드 사용

**캐싱 전략:**
- 버스 도착 정보: 실시간성 중시 → 캐싱 미적용 (또는 짧은 TTL)
- 노선 정보 (막차): 변화 적음 → 1일 또는 수동 무효화

### 2.2.3 API 선택 및 우선순위

| 조회 유형 | 주 API | 보조 API | 용도 |
|----------|--------|---------|------|
| 경로 탐색 | ODsay searchSubwaySchedule + searchBusLane | - | 목적지까지 막차 경로 안내 |
| 버스 막차 (정확도) | Gyeonggi (노선 정보) + Seoul (도착 정보) | ODsay busLastTime | 실시간 + 정적 시간표 하이브리드 |
| 지하철 막차 | ODsay searchSubwaySchedule | - | 지하철은 ODsay 단일 사용 |

## 2.3 Redis 도입

- Refresh Token: DB 컬럼 저장 시 갱신마다 UPDATE 발생 + TTL 자동 만료 불가
- ODsay 캐싱: 일 3,000 호출 제한 대응

## 2.4 Kafka 미도입

- 일 알림 발송 건수 수백 건 예상 → Kafka 오버엔지니어링
- DB 기반 @Scheduled 폴링으로 서버 재시작 후 알림 유실 없음
- 향후 확장 시 NotificationSender 인터페이스로 Kafka 구현체 교체 가능하도록 추상화

## 2.5 ODsay Server 플랫폼

- 백엔드에서 REST 호출 → Server 플랫폼 선택 (URI/Web 아님)
- IP 기반 인증 → 로컬 개발 시 공인 IP 등록 필요
- API Key 프론트 노출 금지

---

# 3. Backend Package Structure

## 3.1 패키지 구조

```
com.lasttrain
├── auth
│   ├── controller        AuthController
│   ├── service           AuthService
│   ├── repository        UserRepository
│   ├── domain            User.java (JPA Entity)
│   ├── dto               SignupRequest, LoginRequest, TokenResponse
│   └── jwt               JwtTokenProvider, JwtAuthFilter
│
├── route
│   ├── controller        RouteController
│   ├── service           RouteService, LastTrainCalculator
│   └── dto               RouteRequest, RouteResponse, TransferDto
│   (repository 없음 - ODsay 실시간 조회, DB 테이블 없음)
│
├── favorite
│   ├── controller        FavoriteController
│   ├── service           FavoriteService
│   ├── repository        FavoriteRepository
│   ├── domain            Favorite.java
│   └── dto               FavoriteRequest, FavoriteResponse
│
├── notification
│   ├── controller        NotificationController
│   ├── service           NotificationService
│   ├── scheduler         NotificationScheduler
│   ├── repository        SubscriptionRepository, ScheduleRepository
│   ├── domain            NotificationSubscription, NotificationSchedule
│   └── dto               SubscribeRequest
│
├── external
│   ├── odsay
│   │   ├── OdsayClient           (인터페이스 - 테스트 Mock 용이)
│   │   ├── OdsayClientImpl       (RestTemplate 구현체)
│   │   ├── dto                   OdsayRouteResponse, OdsayBusLaneResponse 등
│   │   └── OdsayProperties       (API Key, Base URL 설정)
│   └── kakao
│       ├── KakaoAuthClient       (OAuth2.0 토큰 교환)
│       ├── KakaoUserResponse
│       └── KakaoProperties
│
└── global
    ├── config
    │   ├── SecurityConfig
    │   ├── RedisConfig
    │   ├── SwaggerConfig
    │   ├── RestTemplateConfig
    │   └── WebConfig             (CORS)
    ├── exception
    │   ├── GlobalExceptionHandler
    │   ├── ErrorCode             (Enum)
    │   └── AppException          (RuntimeException 상속)
    ├── response
    │   └── ApiResponse<T>        (공통 응답 래퍼)
    └── security
        └── SecurityUserDetails
```

## 3.2 계층별 책임 (Layered Architecture)

### Presentation Layer (controller)
- 담당: HTTP 요청 수신, 파라미터 검증(@Valid), 응답 포맷 조립
- 허용 의존성: Service, DTO
- 금지: 비즈니스 로직, Repository 직접 호출, 외부 API 호출

### Application Layer (service)
- 담당: 유스케이스 실행, 트랜잭션 경계, 도메인 객체 조합
- 허용 의존성: Repository, 외부 클라이언트 인터페이스, 이벤트 발행
- 금지: HTTP 관련 코드(HttpServletRequest 등), Response 객체 직접 생성

### Domain Layer (domain)
- 담당: JPA Entity, 도메인 규칙 메서드
- 허용 의존성: 없음 (순수 Java)
- 금지: Spring 의존성, 외부 API 의존성

### Infrastructure Layer (repository, external)
- 담당: DB 접근(JPA), 외부 API 호출(ODsay, Kakao)
- 허용 의존성: JPA, RestTemplate, Redis
- 금지: 비즈니스 로직

## 3.3 Service 비대화 방지 전략

RouteService가 비대화될 위험이 가장 큼.
분리 전략:

```
RouteService
  └── OdsayClient.searchRoute()        (external - ODsay 호출)
  └── LastTrainCalculator.calculate()  (service - 막차 역산 순수 로직)
  └── RouteResponseMapper.toDto()      (dto - 응답 변환)
```

LastTrainCalculator는 외부 의존성 없는 순수 비즈니스 로직 클래스.
단위 테스트 작성 용이.

## 3.4 외부 API 의존성 분리 전략

OdsayClient를 인터페이스로 선언:

```java
public interface OdsayClient {
  OdsayRouteResponse searchRoute(double sx, double sy, double ex, double ey);
  OdsaySubwayScheduleResponse searchSubwaySchedule(String stationId, String dayType);
  OdsayBusLaneResponse searchBusLane(String busId);
}
```

테스트 시 MockOdsayClient 주입 → ODsay 없이 단위 테스트 가능.
ODsay 스펙 변경 시 OdsayClientImpl만 수정.

## 3.5 트랜잭션 범위 전략

| 메서드                              | @Transactional | 이유                                         |
|-------------------------------------|----------------|----------------------------------------------|
| AuthService.signup()                | 필요           | user 저장 (Redis는 트랜잭션 외)              |
| AuthService.reissue()               | 불필요         | DB 쓰기 없음                                 |
| RouteService.findLastTrainRoutes()  | 불필요         | DB 조회 없음, ODsay 호출만                   |
| FavoriteService.create/update/delete| 필요           | DB 쓰기 있음                                 |
| NotificationService.subscribe()     | 필요           | subscription + schedule 동시 저장            |
| NotificationScheduler.poll()        | 필요           | 발송 + notified 플래그 업데이트 원자성       |

---

# 4. Frontend Package Structure

## 4.1 폴더 구조

```
src
├── api                  백엔드 API 호출 함수 (axios 인스턴스 기반)
│   ├── axiosInstance.ts  인터셉터 설정 (AT 자동 주입, 401 시 reissue)
│   ├── authApi.ts        회원가입, 로그인, 토큰 재발급, 로그아웃
│   ├── routeApi.ts       막차 경로 조회
│   ├── favoriteApi.ts    즐겨찾기 CRUD
│   └── notificationApi.ts 알림 구독/취소
│
├── components           재사용 가능한 UI 컴포넌트 (렌더링 전담, API 호출 금지)
│   ├── common
│   │   ├── Button.tsx
│   │   ├── Input.tsx
│   │   ├── Modal.tsx
│   │   └── LoadingSpinner.tsx
│   ├── route
│   │   ├── RouteCard.tsx        경로 카드
│   │   ├── TransferBadge.tsx    BUS/SUBWAY 태그
│   │   ├── CountdownTimer.tsx   실시간 카운트다운
│   │   └── UrgentBanner.tsx     막차 임박 배너 (30분 이하)
│   ├── search
│   │   ├── SearchOverlay.tsx    도착지 검색 오버레이
│   │   ├── PlaceItem.tsx        장소 검색 결과 항목
│   │   └── RecentSearchChip.tsx 최근 검색 칩
│   ├── favorite
│   │   ├── FavoriteCard.tsx
│   │   └── FavoriteEditModal.tsx
│   └── notification
│       └── PushConsentModal.tsx 알림 구독 동의 모달
│
├── pages                라우트 단위 페이지 (데이터 페칭 + 상태 관리)
│   ├── HomePage.tsx     홈/검색 화면
│   ├── ResultPage.tsx   막차 조회 결과 화면
│   ├── LoginPage.tsx    로그인 화면
│   ├── SignupPage.tsx   회원가입 화면
│   └── FavoritePage.tsx 즐겨찾기 목록 화면
│
├── hooks                커스텀 훅
│   ├── useGeolocation.ts     GPS 위치 감지
│   ├── useCountdown.ts       실시간 카운트다운 타이머
│   ├── useKakaoSearch.ts     카카오 장소 검색 + 디바운싱
│   └── usePushSubscription.ts Web Push 구독 처리
│
├── services             순수 비즈니스 로직 (API 호출 아닌 것)
│   ├── pushService.ts        VAPID 구독 생성 로직
│   └── storageService.ts     localStorage 최근 검색 관리
│
├── stores               전역 상태 (Context API 기반)
│   └── AuthContext.tsx       로그인 상태, Access Token, userId
│
├── layouts
│   └── MainLayout.tsx        공통 헤더/네비게이션
│
├── routes
│   └── AppRouter.tsx         React Router 설정, PrivateRoute
│
├── utils
│   ├── timeUtils.ts          막차 시각 파싱, 남은 시간 계산
│   └── formatUtils.ts        날짜/시간 포맷팅
│
└── types
    ├── route.ts              RouteResponse, Transfer 타입
    ├── favorite.ts           Favorite 타입
    └── auth.ts               User, Token 타입
```

## 4.2 Atomic Design 미적용 이유

화면 5개, 컴포넌트 수십 개 규모에서 Atomic Design은 오버엔지니어링.
atoms/molecules/organisms/templates 뎁스를 추가해도 실익 없음.
components/common (공통) + components/기능별 2단계로 충분.

## 4.3 Presentational / Container 분리

엄격한 분리 대신 아래 원칙만 준수:

- `pages/*`: 데이터 페칭 + 상태 관리 + 컴포넌트 조합 (Container 역할)
- `components/*`: props 받아 렌더링만 담당. 직접 API 호출 금지.

```
ResultPage.tsx
  → routeApi.getLastTrain() 호출
  → RouteCard에 { departureDeadline, transfers, canCatch } props 전달

RouteCard.tsx
  → props만으로 렌더링. API 호출 없음.
```

## 4.4 상태 관리 전략

Context API로 충분. Zustand/Redux 불필요.

| 상태              | 관리 방법                     | 이유                              |
|-------------------|-------------------------------|-----------------------------------|
| 로그인 여부       | AuthContext                   | 전역 필요                         |
| Access Token      | AuthContext (메모리)          | XSS 방지                          |
| userId            | AuthContext                   | 전역 필요                         |
| 막차 경로 결과    | ResultPage useState           | 해당 페이지에서만 필요            |
| 즐겨찾기 목록     | FavoritePage useState         | 해당 페이지에서만 필요            |
| 검색 오버레이     | HomePage useState             | 로컬 UI 상태                      |
| 모달 열림 여부    | 각 컴포넌트 useState          | 로컬 UI 상태                      |
| 최근 검색 목록    | localStorage (storageService) | 영속 필요, 서버 저장 불필요       |
| 카운트다운        | useCountdown 훅               | 컴포넌트 격리                     |

## 4.5 API 호출 계층

```typescript
// api/axiosInstance.ts
const axiosInstance = axios.create({
  baseURL: process.env.REACT_APP_API_URL,
});

// AT 자동 주입
axiosInstance.interceptors.request.use(config => {
  const token = getAccessToken(); // AuthContext
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 401 시 자동 reissue + 재시도
axiosInstance.interceptors.response.use(
  res => res,
  async err => {
    if (err.response?.status === 401 && !err.config._retry) {
      err.config._retry = true;
      const newToken = await authApi.reissue();
      setAccessToken(newToken); // AuthContext 업데이트
      err.config.headers.Authorization = `Bearer ${newToken}`;
      return axiosInstance(err.config);
    }
    return Promise.reject(err);
  }
);
```

## 4.6 로딩/에러 상태 처리

각 페이지에서 isLoading, error 상태 관리:

```typescript
const [routes, setRoutes] = useState<Route[]>([]);
const [isLoading, setIsLoading] = useState(false);
const [error, setError] = useState<string | null>(null);

const handleSearch = async () => {
  setIsLoading(true);
  setError(null);
  try {
    const res = await routeApi.getLastTrain(params);
    setRoutes(res.data.routes);
  } catch {
    setError('경로 조회 중 오류가 발생했습니다.');
  } finally {
    setIsLoading(false);
  }
};
```

## 4.7 CountdownTimer 구현

서버에서 departureDeadline(HH:mm)을 받아 클라이언트에서 카운트다운.

```typescript
// hooks/useCountdown.ts
export function useCountdown(departureDeadline: string) {
  const [minutesLeft, setMinutesLeft] = useState(0);

  useEffect(() => {
    const calc = () => {
      const now = new Date();
      const [h, m] = departureDeadline.split(':').map(Number);
      const deadline = new Date();
      deadline.setHours(h, m, 0, 0);

      // 자정 넘김 처리: deadline이 이미 지났으면 다음날로 간주
      if (deadline < now) deadline.setDate(deadline.getDate() + 1);

      setMinutesLeft(Math.max(0,
        Math.floor((deadline.getTime() - now.getTime()) / 60000)
      ));
    };

    calc();
    const timer = setInterval(calc, 60000);
    return () => clearInterval(timer);
  }, [departureDeadline]);

  return minutesLeft;
}
```

## 4.8 Web Push 권한 요청 UI 흐름

```
[흐름 1 - 회원가입 직후]
SignupPage → 가입 성공 → PushConsentModal 노출
  '허용' → usePushSubscription.subscribe()
          → navigator.serviceWorker.register('/service-worker.js')
          → PushManager.subscribe({
              userVisibleOnly: true,
              applicationServerKey: VAPID_PUBLIC_KEY
            })
          → notificationApi.subscribe({ endpoint, auth, p256dh, lastBoardTime, ... })
  '나중에' → 모달 닫기

[흐름 2 - 막차 결과 화면]
ResultPage → RouteCard 하단 '알림 받기' 버튼
  → 구독 미완료 시 PushConsentModal 노출
  → 구독 완료 시 notificationApi.subscribe(...)
```

---

# 5. Core Features

## 5.1 막차 역산 (핵심 비즈니스 로직)

```
[Step 1] ODsay 경로 탐색 (SX/SY/EX/EY)
[Step 2] 구간(subPath)별 trafficType 분리 (1=버스, 2=지하철)
[Step 3] 구간별 막차 시각 조회
  → 지하철: searchSubwaySchedule(stationId, dayType) → firstLastFlag=2
  → 버스: searchBusLane(busId) → busLastTime
[Step 4] 역산: 마지막 구간부터 소요시간 차감 → departureDeadline
[Step 5] canCatch, minutesLeft 계산 (Asia/Seoul 기준)
[Step 6] departureDeadline 늦은 순 정렬 후 응답
```

## 5.2 웹 푸시 알림

```
[구독]
 브라우저 Push 권한 → endpoint/auth/p256dh 획득
 → POST /api/v1/notifications/subscribe
 → notification_subscription + notification_schedule 저장

[발송]
 @Scheduled(fixedDelay=60000)
 → WHERE notified_30min=false AND last_board_time BETWEEN now+29 AND now+31
 → Web Push 발송 → notified_30min=true
```

---

# 6. API Design

## 6.1 전체 API 목록

| Method | URL                             | Auth  | 설명                  |
|--------|---------------------------------|-------|-----------------------|
| GET    | /api/v1/last-train              | 불필요 | 막차 경로 조회        |
| POST   | /api/v1/auth/signup             | 불필요 | 이메일 회원가입       |
| POST   | /api/v1/auth/login              | 불필요 | 로그인 (JWT 발급)     |
| POST   | /api/v1/auth/kakao              | 불필요 | 카카오 소셜 로그인    |
| POST   | /api/v1/auth/reissue            | RT    | Access Token 재발급   |
| POST   | /api/v1/auth/logout             | 필요  | 로그아웃 (RT 삭제)    |
| GET    | /api/v1/favorites               | 필요  | 즐겨찾기 목록 조회    |
| POST   | /api/v1/favorites               | 필요  | 즐겨찾기 등록         |
| PUT    | /api/v1/favorites/{id}          | 필요  | 즐겨찾기 수정         |
| DELETE | /api/v1/favorites/{id}          | 필요  | 즐겨찾기 삭제         |
| POST   | /api/v1/notifications/subscribe | 필요  | 웹 푸시 구독 저장     |
| DELETE | /api/v1/notifications/{id}      | 필요  | 알림 예약 취소        |

## 6.2 공통 응답 포맷

성공:

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "timestamp": "2026-05-12T21:00:00"
}
```

실패:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ODSAY_API_ERROR",
    "message": "경로 조회 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
  },
  "timestamp": "2026-05-12T21:00:00"
}
```

## 6.3 GET /api/v1/last-train Response

```json
{
  "success": true,
  "data": {
    "origin": "강남구청",
    "destination": "부천역",
    "date": "2026-04-06",
    "dayType": "WEEKDAY",
    "routes": [
      {
        "departureDeadline": "23:11",
        "currentStatus": {
          "canCatch": true,
          "minutesLeft": 35,
          "message": "막차까지 35분 남았어요!"
        },
        "transfers": [
          {
            "type": "SUBWAY",
            "line": "2호선",
            "boardAt": "강남역",
            "alightAt": "신도림역",
            "lastBoardTime": "23:11"
          },
          {
            "type": "BUS",
            "line": "5620번",
            "boardAt": "신도림역",
            "alightAt": "부천역",
            "lastBoardTime": "23:28"
          }
        ]
      }
    ]
  },
  "error": null,
  "timestamp": "2026-04-06T22:36:00"
}
```

## 6.4 ErrorCode Enum

```java
public enum ErrorCode {
  EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
  TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
  REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "Refresh Token이 일치하지 않습니다."),
  ODSAY_API_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "경로 조회 중 오류가 발생했습니다."),
  NO_ROUTE_FOUND(HttpStatus.NOT_FOUND, "해당 경로를 찾을 수 없습니다."),
  FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "즐겨찾기를 찾을 수 없습니다."),
  FAVORITE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 즐겨찾기만 수정할 수 있습니다."),
  SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 정보를 찾을 수 없습니다."),
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

  private final HttpStatus httpStatus;
  private final String message;
}
```

---

# 7. Swagger / OpenAPI Strategy

## 7.1 의존성

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
```

## 7.2 경로

- Swagger UI  : `/swagger-ui.html`
- OpenAPI 3.0 : `/v3/api-docs`

운영 환경 비활성화 (`application-prod.yml`):

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

## 7.3 JWT Bearer 인증 설정

```java
@Bean
public OpenAPI openAPI() {
  SecurityScheme securityScheme = new SecurityScheme()
    .type(SecurityScheme.Type.HTTP)
    .scheme("bearer")
    .bearerFormat("JWT")
    .in(SecurityScheme.In.HEADER)
    .name("Authorization");

  return new OpenAPI()
    .info(new Info().title("막차 알리미 API").version("v1"))
    .components(new Components()
      .addSecuritySchemes("BearerAuth", securityScheme))
    .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
}
```

Authorization 헤더:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## 7.4 API 태그 분류

```java
@Tag(name = "Auth",         description = "회원가입, 로그인, 토큰 관리")
@Tag(name = "Route",        description = "막차 경로 조회")
@Tag(name = "Favorite",     description = "즐겨찾기 CRUD")
@Tag(name = "Notification", description = "웹 푸시 구독 관리")
```

## 7.5 GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(AppException.class)
  public ResponseEntity<ApiResponse<?>> handleApp(AppException e) {
    return ResponseEntity
      .status(e.getErrorCode().getHttpStatus())
      .body(ApiResponse.error(e.getErrorCode()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<?>> handleValidation(
      MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldErrors().stream()
      .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
      .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest()
      .body(ApiResponse.error("INVALID_INPUT", msg));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<?>> handleUnknown(Exception e) {
    log.error("[UnhandledException]", e);
    return ResponseEntity.internalServerError()
      .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
  }
}
```

---

# 8. Authentication Strategy

## 8.1 JWT 설정

| 항목             | 값                             |
|------------------|--------------------------------|
| Access Token     | 유효기간 30분                  |
| Refresh Token    | 유효기간 7일                   |
| 서명 알고리즘    | HS256                          |
| AT 저장 (프론트) | 메모리 (AuthContext) 권장      |
| RT 저장 (서버)   | Redis (`RT:{userId}`, TTL 7일) |

## 8.2 RT Rotation

```
POST /api/v1/auth/reissue
 Authorization: Bearer {refreshToken}

1. RT 서명/만료 검증
2. Redis "RT:{userId}" 값과 비교
3. 일치 시 새 AT + RT 발급
4. Redis에 새 RT 덮어쓰기
```

## 8.3 로그아웃

```
POST /api/v1/auth/logout (인증 필요)

1. Redis "RT:{userId}" 삭제
2. 프론트: AuthContext 초기화
```

## 8.4 카카오 OAuth2.0

```
[프론트]
1. 카카오 인가 코드 요청 → 카카오 로그인 redirect
2. redirect_uri로 인가 코드 수신
3. POST /api/v1/auth/kakao { "code": "인가코드" }

[백엔드]
4. 인가 코드 → 카카오 토큰 교환
5. 카카오 토큰 → 사용자 정보 조회
6. user 테이블 upsert (provider=KAKAO)
7. JWT 발급 응답
```

---

# 9. ERD

## 9.1 테이블 관계

```
user (1) ──────────── (N) favorite
user (1) ──────────── (N) notification_subscription
notification_subscription (1) ── (N) notification_schedule
```

## 9.2 user

```sql
CREATE TABLE user (
  user_id     BIGINT       AUTO_INCREMENT PRIMARY KEY,
  email       VARCHAR(100) UNIQUE,
  password    VARCHAR(255),
  provider    ENUM('EMAIL','KAKAO') NOT NULL DEFAULT 'EMAIL',
  provider_id VARCHAR(100),
  -- refresh_token 컬럼 없음. Redis로 관리.
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_provider (provider, provider_id)
);
```

## 9.3 favorite

```sql
CREATE TABLE favorite (
  favorite_id BIGINT        AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT        NOT NULL,
  name        VARCHAR(50)   NOT NULL,
  emoji       VARCHAR(10),
  lat         DECIMAL(10,7) NOT NULL,
  lng         DECIMAL(10,7) NOT NULL,
  address     VARCHAR(200),
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE,
  INDEX idx_favorite_user (user_id)
);
```

## 9.4 notification_subscription

```sql
CREATE TABLE notification_subscription (
  subscription_id BIGINT       AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT       NOT NULL,
  endpoint        VARCHAR(500) NOT NULL,
  auth            VARCHAR(100) NOT NULL,
  p256dh          VARCHAR(200) NOT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE,
  UNIQUE KEY uq_subscription (user_id, endpoint)
);
```

## 9.5 notification_schedule

```sql
-- Redis ZSET Delay Queue 방식으로 변경.
-- notified_30min / notified_10min 컬럼 제거.
-- 알림 실행 상태는 Redis가 관리 (pop하면 자동 제거).
-- DB는 순수 예약 데이터 저장 역할만 수행.
-- last_board_time: TIME 아님! DATETIME 필수 (자정 넘김 처리)
CREATE TABLE notification_schedule (
  schedule_id     BIGINT       AUTO_INCREMENT PRIMARY KEY,
  subscription_id BIGINT       NOT NULL,
  origin          VARCHAR(100) NOT NULL,
  destination     VARCHAR(100) NOT NULL,
  last_board_time DATETIME     NOT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (subscription_id)
    REFERENCES notification_subscription(subscription_id) ON DELETE CASCADE
);
```

---

# 10. Last Train Calculation Flow

## 10.1 상세 흐름

```
RouteController
  → [favoriteId 있으면] FavoriteService.getCoordinate() (userId 검증 포함)
  → RouteService.findLastTrainRoutes(originCoord, destCoord)
      → Redis 캐시 확인 ("odsay:route:{sx}:{sy}:{ex}:{ey}:{dayType}")
      → 캐시 미스 시 OdsayClient.searchRoute() 호출 → Redis 저장 (TTL 1시간)
      → LastTrainCalculator.calculate(paths, now)
          → 각 경로별 구간 파싱
          → 지하철: searchSubwaySchedule() → firstLastFlag=2
          → 버스: searchBusLane() → busLastTime
          → 역산 → departureDeadline
          → canCatch, minutesLeft 계산
          → departureDeadline 내림차순 정렬
      → RouteResponse 조립 후 반환
```

## 10.2 dayType 판별

```java
public DayType resolveDayType(LocalDate date) {
  return switch (date.getDayOfWeek()) {
    case SATURDAY -> DayType.SAT;
    case SUNDAY   -> DayType.SUN;
    default       -> DayType.WEEKDAY;
  };
}
```

## 10.3 시간 계산 원칙

- 모든 시간: `ZoneId.of("Asia/Seoul")`
- `LocalTime.now()` 직접 사용 금지.
- ODsay `"24:10"` 형태 파싱 → Edge Cases 18절 참고.

---

# 11. Scheduler & Notification Flow

## 11.1 변경 이유: DB polling → Redis Delay Queue

기존 DB polling 방식의 문제점:
- `@Scheduled(60초)`: 최대 60초 알림 지연 발생
- `notified` 컬럼: DB가 실행 상태 머신 역할 겸임 (관심사 혼재)
- BETWEEN 쿼리 + flag UPDATE: 다중 서버 시 중복 발송 위험

Redis ZSET Delay Queue 방식:
- `@Scheduled(1초)` + Redis in-memory 조회: 부하 없이 1초 이내 정확도
- Lua script atomic pop: 중복 실행 없음 (scale-out 안전)
- DB는 순수 저장소 역할만 유지

## 11.2 Redis Delay Queue 구조

```
Key  : "notification:queue"              (ZSET 이름, 고정)
Value: "{scheduleId}:{minutesBefore}"    예) "42:30", "42:10"
Score: execution timestamp (epoch ms)   실행해야 할 Unix 시간(밀리초)

등록 예시:
  scheduleId=42, lastBoardTime=2026-05-11 23:11
  → ZADD notification:queue 1747063260000 "42:30"  (22:41의 epoch ms)
  → ZADD notification:queue 1747064460000 "42:10"  (23:01의 epoch ms)
```

## 11.3 알림 등록 흐름 (subscribe 시점)

```
POST /api/v1/notifications/subscribe
  → NotificationService.subscribe()
      → DB: notification_subscription INSERT
      → DB: notification_schedule INSERT (origin, destination, last_board_time)
      → Redis: NotificationQueueService.enqueue(scheduleId, lastBoardTime)
               ZADD notification:queue <score30> "{scheduleId}:30"
               ZADD notification:queue <score10> "{scheduleId}:10"
```

## 11.4 알림 발송 흐름 (Redis Worker)

```
@Scheduled(fixedDelay=1_000)  ← 1초마다 실행
  → NotificationQueueService.popDue()
       Lua script: ZRANGEBYSCORE notification:queue 0 <now> LIMIT 0 100
                   ZREM (조회와 삭제를 원자적으로 처리 → 중복 방지)
  → 결과 예) ["42:30", "55:10"]
  → 항목별 처리:
       scheduleId, minutesBefore 파싱
       → DB: ScheduleRepository.findWithSubscription(scheduleId)
       → WebPushService.send(subscription, message)
       → 성공: Redis에서 이미 제거됨 (atomic pop 시점에 제거)
       → 실패: 로그 기록, 계속 진행 (MVP: retry 미포함)
```

## 11.5 알림 취소 흐름

```
DELETE /api/v1/notifications/{id}
  → NotificationService.cancel(scheduleId, userId)
      → Redis: NotificationQueueService.cancel(scheduleId)
               ZREM notification:queue "{scheduleId}:30" "{scheduleId}:10"
      → DB: notification_schedule DELETE
```

## 11.6 VAPID 설정

```yaml
vapid:
  public-key: ${VAPID_PUBLIC_KEY}
  private-key: ${VAPID_PRIVATE_KEY}
  subject: mailto:admin@example.com
```

라이브러리: `nl.martijndwars:web-push:5.x.x`

---

# 12. Event Flow

## 12.1 적용 이벤트

| 이벤트                      | 발행 시점                           | 동기/비동기   | MVP 리스너   |
|-----------------------------|-------------------------------------|---------------|--------------|
| UserSignedUpEvent           | AuthService.signup() 완료 후        | @Async 비동기 | 미구현 (향후)|
| NotificationSubscribedEvent | NotificationService.subscribe() 후  | @Async 비동기 | 미구현 (향후)|

## 12.2 코드 예시

```java
// 이벤트 발행
publisher.publishEvent(new UserSignedUpEvent(user.getId(), user.getEmail()));

// 리스너 (향후 확장)
@EventListener
@Async
public void onSignedUp(UserSignedUpEvent event) {
  // TODO: 환영 알림 등
}
```

## 12.3 이벤트 남용 방지

단순 CRUD에는 이벤트 사용 금지. Service 직접 호출.
이벤트 사용 기준:
- 트랜잭션 완료 후 부가 작업 (실패해도 메인 플로우 영향 없음)
- 여러 리스너가 동일 이벤트 처리 시

## 12.4 Kafka 미도입 근거

일 알림 수백 건 규모. Kafka 운영 비용 불필요.
DB 기반 @Scheduled 폴링으로 메시지 유실 없음.

---

# 13. Redis Usage

## 13.1 사용 범위

| 용도                    | 사용 | 근거                                                  |
|-------------------------|------|-------------------------------------------------------|
| Refresh Token           | ✅   | TTL 자동 만료, RT Rotation, 로그아웃 즉시 무효화      |
| ODsay 응답 캐싱         | ✅   | 일 3,000 호출 제한 대응. 필수.                        |
| 알림 Delay Queue (ZSET) | ✅   | Scheduler execution engine. atomic pop으로 중복 방지. |
| API Rate Limiting       | ❌   | MVP 불필요                                            |
| 실시간 남은시간 캐싱    | ❌   | 클라이언트 카운트다운으로 처리                        |

## 13.2 Refresh Token

```
key: "RT:{userId}"    TTL: 7일
로그인: SET
재발급: SET (덮어쓰기, 기존 무효화)
로그아웃: DEL
```

## 13.3 ODsay 응답 캐싱

```
key: "odsay:route:{sx}:{sy}:{ex}:{ey}:{dayType}"
TTL: 3600초 (1시간)
```

dayType 키 포함 필수 (평일/주말 시간표 다름).
00:00~02:00 구간은 TTL 30분으로 단축 검토 (dayType 경계 넘김).

## 13.4 알림 Delay Queue (ZSET)

```
key  : "notification:queue"
type : Sorted Set (ZSET)
value: "{scheduleId}:{minutesBefore}"  예) "42:30", "42:10"
score: execution timestamp (epoch milliseconds)

구독 등록 시: ZADD notification:queue <score> <value>
Worker 실행 시: Lua script atomic ZRANGEBYSCORE + ZREM
알림 취소 시:  ZREM notification:queue "{id}:30" "{id}:10"
TTL: 없음 (발송 완료 항목은 Worker가 pop 시 자동 제거)
```

---

# 14. Error Handling Strategy

## 14.1 예외 계층

AppException 단일 클래스 + ErrorCode Enum으로 관리.
서브클래스 분리는 MVP 규모에서 불필요.

## 14.2 외부 API 예외

```java
try {
  return odsayClient.searchRoute(sx, sy, ex, ey);
} catch (Exception e) {
  log.error("[ODsay Error] {}", e.getMessage());
  throw new AppException(ErrorCode.ODSAY_API_ERROR);
}
```

## 14.3 즐겨찾기 소유권 검증

```java
Favorite fav = favoriteRepository.findById(id)
  .orElseThrow(() -> new AppException(ErrorCode.FAVORITE_NOT_FOUND));
if (!fav.getUser().getId().equals(currentUserId))
  throw new AppException(ErrorCode.FAVORITE_ACCESS_DENIED);
```

---

# 15. Performance & Caching

## 15.1 ODsay 호출 수 추산

캐싱 없이: 평균 4회/요청 × 700명 = 2,800회 → 한도 근접
Redis 캐싱 필수.

## 15.2 ODsay 병렬 호출 개선 (향후)

- 현재: 구간별 막차 조회 직렬 (200ms × 3구간 = 600ms)
- 개선: CompletableFuture 병렬 처리 검토

## 15.3 인덱스

```sql
INDEX idx_favorite_user    ON favorite (user_id);
INDEX idx_schedule_polling ON notification_schedule
  (last_board_time, notified_30min, notified_10min);
```

---

# 16. Security Considerations

## 16.1 비밀번호

BCryptPasswordEncoder (strength 10).

## 16.2 필수 환경변수

```
JWT_SECRET              (32자 이상)
ODSAY_API_KEY           (프론트 노출 금지)
KAKAO_CLIENT_ID
KAKAO_CLIENT_SECRET
VAPID_PUBLIC_KEY
VAPID_PRIVATE_KEY
```

## 16.3 CORS

```java
config.setAllowedOrigins(List.of("http://localhost:3000", "https://yourdomain.com"));
config.setAllowCredentials(true);
```

## 16.4 HTTPS 필수 이유

Geolocation API, Web Push, Service Worker 모두 HTTPS 필수.
localhost는 예외. 스테이징 환경에서 반드시 확인.

---

# 17. Frontend State Management

## 17.1 AuthContext 구조

```typescript
interface AuthContextType {
  isLoggedIn: boolean;
  userId: number | null;
  accessToken: string | null;
  login: (res: TokenResponse) => void;
  logout: () => void;
}

// 새로고침 시 자동 reissue
useEffect(() => {
  authApi.reissue()
    .then(res => { setAccessToken(res.accessToken); setUserId(res.userId); })
    .catch(() => {}); // 미로그인 → 무시
}, []);
```

## 17.2 서버 상태와 UI 상태 분리 원칙

서버 상태(막차 결과, 즐겨찾기)는 AuthContext에 넣지 않는다.
해당 페이지에서 직접 fetch + useState.
페이지 진입 시마다 최신 데이터 보장.

## 17.3 React Query 미도입 이유

막차 조회: 버튼 클릭 1회 fetch, 자동 캐싱 불필요.
즐겨찾기: 페이지 진입 fetch, mutation 후 수동 재fetch.
React Query의 강점이 이 패턴에서 불필요. useState로 충분.

## 17.4 PrivateRoute

```typescript
const PrivateRoute = ({ children }) => {
  const { isLoggedIn } = useAuth();
  return isLoggedIn ? children : <Navigate to="/login" replace />;
};
```

---

# 18. Edge Cases

## 18.1 자정 넘김 (최우선 처리)

ODsay 응답 `"24:10"` 형태 파싱:

```java
public LocalDateTime parseLastBoardTime(String timeStr, LocalDate baseDate) {
  int hour = Integer.parseInt(timeStr.split(":")[0]);
  int min  = Integer.parseInt(timeStr.split(":")[1]);
  if (hour >= 24) return baseDate.plusDays(1).atTime(hour - 24, min);
  return baseDate.atTime(hour, min);
}
```

`notification_schedule.last_board_time`이 DATETIME이므로 정상 저장.
프론트 CountdownTimer에서도 동일 처리 (4.7절 참고).

## 18.2 Timezone

```java
@PostConstruct
public void init() {
  TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
}
```

```yaml
spring.jpa.properties.hibernate.jdbc.time_zone: Asia/Seoul
```

## 18.3 동시성: 알림 중복 발송

단일 인스턴스에서는 @Scheduled 단일 스레드 → 중복 없음.
다중 인스턴스 시: shedlock 도입 필요.

## 18.4 busLastTime null

```java
if (busLastTime == null || busLastTime.isBlank()) {
  log.warn("[버스 막차 정보 없음] busId={}", busId);
  return null; // transfer.lastBoardTime = null
}
```

프론트: `{transfer.lastBoardTime ?? '정보 없음'}`

## 18.5 비인증 사용자의 favoriteId 사용

```java
if (favoriteId != null && currentUserId == null) {
  throw new AppException(ErrorCode.INVALID_INPUT);
}
```

## 18.6 ODsay 한도 초과

에러 응답 → ODSAY_API_ERROR.
Redis 캐싱으로 예방. 초과 시 로그 모니터링.

---

# 19. Technical Decisions

| 결정                    | 선택              | 이유                                       |
|-------------------------|-------------------|--------------------------------------------|
| ORM                     | Spring Data JPA   | 4테이블 단순 구조, JPA 직접 경험 목적      |
| 버스 API                | ODsay 단일        | busLastTime으로 충분                       |
| 알림 비동기             | @Scheduled        | Kafka 과함, DB 기반 내구성                 |
| RT 저장                 | Redis             | TTL 자동 만료, 즉시 무효화                 |
| AT 저장                 | 메모리 (Context)  | XSS 방지                                   |
| 장소 검색               | 프론트 직접       | 백엔드 경유 불필요                         |
| ODsay 플랫폼            | Server            | 백엔드 호출, IP 기반 인증                  |
| 전역 상태 관리          | Context API       | 전역 상태가 인증뿐, 라이브러리 불필요      |
| 서버 상태 관리          | useState          | React Query 불필요한 규모                  |
| Atomic Design           | 미적용            | 화면 5개 수준, 오버엔지니어링              |
| 막차 정보 기반          | 정적 시간표       | 실시간 버스 위치 미지원, 안내 문구로 보완  |

---

# 20. Known Risks

> **[HIGH]** notification_schedule.last_board_time 타입
> - TIME → 자정 넘김 오작동. DATETIME으로 변경 필수.

> **[HIGH]** ODsay 일 3,000 호출 한도
> - 캐싱 없이 700명 이상에서 초과. Redis 캐싱 필수.

> **[HIGH]** HTTPS 미설정
> - GPS, Web Push, Service Worker 불동작. 배포 전 SSL 필수.

> **[MEDIUM]** 막차 정보 부정확
> - busLastTime 정적 시간표. 결과 화면 안내 문구 필수.

> **[MEDIUM]** AT 새로고침 소실
> - 메모리 저장 → 새로고침 시 소실 → reissue 자동 호출로 복구.

> **[MEDIUM]** 알림 발송 실패 재시도 없음
> - MVP 허용. 향후 retry_count 컬럼 + DLQ(Dead Letter Queue) 구조로 확장.

> **[MEDIUM]** Redis 장애 시 알림 유실
> - Redis 재시작 시 notification:queue ZSET이 초기화됨.
> - 대응: Redis persistence(AOF) 활성화 또는 재구독 안내 처리.

> **[LOW]** 이메일 중복 Race Condition
> - UNIQUE 제약 + DataIntegrityViolationException 처리로 대응.

> **[LOW]** 카카오 첫 로그인 알림 동의 흐름 미정의
> - 신규 user 생성 시 provider 무관하게 PushConsentModal 표시 권장.

---

# 21. TODO Before Production

## P0 - 개발 전 반드시

- [ ] notification_schedule DDL: last_board_time → DATETIME, notified 컬럼 제거
- [ ] user 테이블: refresh_token 컬럼 없이 생성
- [ ] notification_subscription: UNIQUE KEY (user_id, endpoint)
- [ ] 설계 문서 3.3 vs 3.6 응답 포맷 → routes[] 배열로 통일
- [ ] 설계 문서 5.5 timetable 잔존 문구 삭제

## P1 - 개발과 함께

- [ ] ODsay 응답 Redis 캐싱 구현
- [ ] GlobalExceptionHandler + ErrorCode Enum
- [ ] 로그아웃 API (POST /api/v1/auth/logout)
- [ ] 알림 취소 API (DELETE /api/v1/notifications/{id})
- [ ] 자정 넘김 시각 파싱 + 단위 테스트
- [ ] AT 메모리 저장 + 새로고침 시 reissue 자동 호출
- [ ] Service Worker 등록 + 푸시 수신 코드
- [ ] 막차 결과 안내 문구
- [ ] busLastTime null 처리
- [ ] Redis persistence(AOF) 설정 (notification:queue 유실 방지)

## P2 - 배포 전

- [ ] HTTPS SSL 인증서
- [ ] Swagger UI 운영 비활성화
- [ ] 환경변수 체크리스트 전체 점검
- [ ] ODsay Server 플랫폼 운영 IP 등록
- [ ] CORS 운영 도메인으로 변경
- [ ] TimeZone 명시 설정
- [ ] 카카오 redirect_uri 운영 도메인 등록

## P3 - 고도화

- [ ] ODsay 병렬 호출 (CompletableFuture)
- [ ] 알림 재시도 (retry_count)
- [ ] 다중 기기 로그인 지원
- [ ] shedlock (다중 인스턴스)
- [ ] 이메일 인증

---

# Review Findings

## 설계 충돌

**[충돌 1]** notification_schedule.last_board_time 타입
- 설계 문서: TIME 타입
- 자정 넘김(24:10 → 00:10) 막차 케이스에서 날짜 정보 없어 스케줄러 오작동
- 예) 막차 00:10, 30분 전 = 전날 23:40 발송 불가
- 수정: DATETIME 타입. 알림 예약 시 날짜 포함 저장. (본 문서 9.5절 반영)

**[충돌 2]** Refresh Token 이중 전략
- 설계 문서: user.refresh_token 컬럼 + Redis 동시 언급
- 수정: Redis 단일 저장 확정. user 테이블에 컬럼 없이 생성. (본 문서 9.2절 반영)

**[충돌 3]** 3.3 vs 3.6 Response 구조 불일치
- 3.3: routes[] 배열 / 3.6: lastRoute 단일 객체
- 수정: routes[] 배열로 통일. (본 문서 6.3절 반영)

**[충돌 4]** 5.5 데이터 흐름 timetable 문구 잔존
- "DB timetable 조회" 문구가 ODsay 단일 API 결정 후에도 남아있음
- 설계 문서에서 삭제 필요

## 위험 요소

**[위험 1]** ODsay 일 호출 한도 초과 (심각도: HIGH)
- 무캐싱 시 700명 이상에서 초과. 캐싱은 선택이 아닌 필수.

**[위험 2]** HTTPS 없으면 핵심 기능 전부 불동작 (심각도: HIGH)
- Geolocation, Web Push, Service Worker 모두 HTTPS 필수.
- localhost에서 테스트 성공해도 배포 후 실패. 스테이징 환경 확인 필수.

**[위험 3]** 자정 넘김 알림 오발송 (심각도: HIGH)
- TIME 타입 → 개발 후 발견 시 스키마 변경 + 데이터 마이그레이션 필요.
- 개발 전 DATETIME으로 생성 필수.

**[위험 4]** ODsay 단일 의존성 (심각도: MEDIUM)
- ODsay 장애 시 서비스 전면 불가. 대안 없음.
- 친절한 에러 메시지 + 모니터링 알림 설정 권장.

## 누락된 부분

**[누락 1]** 로그아웃 API 미정의
- POST /api/v1/auth/logout 없음. Redis RT 삭제 흐름 필요.

**[누락 2]** 알림 취소 API 미정의
- DELETE /api/v1/notifications/{id} 없음.
- 사용자가 예약된 알림을 취소할 방법 없음.

**[누락 3]** notification_subscription 중복 구독 미처리
- 동일 user + endpoint 중복 구독 시 처리 방식 미정의.
- UNIQUE KEY (user_id, endpoint) + 충돌 시 upsert 처리 필요.

**[누락 4]** 카카오 첫 로그인 알림 동의 흐름
- 이메일 가입만 명시. 카카오 신규 가입 시 동일 모달 여부 미명시.
- 신규 user 생성 시 provider 무관하게 모달 표시 권장.

**[누락 5]** Service Worker 등록 코드
- public/service-worker.js 생성 + 푸시 수신 시 알림 표시 로직 미정의.

**[누락 6]** 카카오 redirect_uri 설정
- 로컬/운영 redirect_uri 카카오 개발자센터 등록 필요. 설계 문서 미명시.

## 성능 이슈 가능성

**[성능 1]** ODsay API 직렬 호출
- 3구간 경로 = 4회 직렬 호출 = 600ms~1,200ms
- Redis 캐싱으로 1차 완화. 향후 CompletableFuture 병렬 처리 검토.

**[성능 2]** 알림 스케줄러 인덱스 누락 시 FULL SCAN
- INDEX idx_schedule_polling 필수.

**[성능 3]** 알림 스케줄러 N+1
- schedule → subscription 조회 시 N+1 발생.
- JOIN FETCH 필수. (11.1절 반영)

## 운영 리스크

**[운영 1]** ODsay 장애 시 서비스 전면 중단
- 단일 외부 API 의존. 대안 없음. 장애 모니터링 알림 설정 권장.

**[운영 2]** VAPID 키 분실 시 기존 구독 전체 무효화
- 환경변수 + 안전한 저장소 필수.

**[운영 3]** ODsay Server 플랫폼 IP 변경 시 인증 실패
- 배포 서버 IP 변경 시 ODsay 앱 설정 즉시 업데이트 필요. 고정 IP 권장.

**[운영 4]** 알림 발송 실패 사용자 인지 불가
- 로그만 남기고 진행. 사용자는 알림 미수신 이유 모름.
- MVP 허용. 향후 실패 카운터 + 안내 추가.

## 수정 추천사항

**[P0 즉시]**
1. DDL: last_board_time → DATETIME
2. user 테이블: refresh_token 컬럼 없이 생성
3. notification_subscription: UNIQUE KEY (user_id, endpoint)
4. 설계 문서 응답 포맷 통일 (routes[] 배열)
5. 설계 문서 timetable 잔존 문구 삭제

**[P1 개발과 함께]**
6. ODsay Redis 캐싱 (필수, 한도 대응)
7. 로그아웃 API
8. 알림 취소 API
9. 자정 넘김 파싱 + 단위 테스트
10. AT 메모리 저장 + reissue 자동 호출

**[P2 배포 전]**
11. HTTPS 설정
12. Swagger 운영 비활성화
13. 환경변수 전체 점검
14. ODsay 운영 IP 등록
15. 카카오 redirect_uri 운영 등록
16. Service Worker + 푸시 수신 코드
