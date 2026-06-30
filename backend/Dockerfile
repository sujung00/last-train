# ──────────────────────────────────────────────────────────────────────────────
# 1단계: 빌드 (Gradle로 jar 생성)
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 의존성 캐시를 활용하기 위해 빌드 스크립트만 먼저 복사합니다.
# build.gradle이 바뀌지 않으면 이 레이어가 캐시되어 빌드 속도가 빨라집니다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드는 마지막에 복사합니다 (소스만 바뀌면 위 의존성 레이어는 캐시 재사용).
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ──────────────────────────────────────────────────────────────────────────────
# 2단계: 실행 (빌드된 jar만 가져와서 가볍게 실행)
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
