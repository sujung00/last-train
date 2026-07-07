# ──────────────────────────────────────────────────────────────────────────────
# 1단계: 프론트엔드 빌드 (React → dist)
# ──────────────────────────────────────────────────────────────────────────────
FROM --platform=linux/amd64 node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ──────────────────────────────────────────────────────────────────────────────
# 2단계: 백엔드 빌드 (Spring Boot → JAR)
# ──────────────────────────────────────────────────────────────────────────────
FROM --platform=linux/amd64 eclipse-temurin:17-jdk AS backend-build
WORKDIR /app
COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
COPY backend/gradle gradle
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true
COPY backend/src src
RUN ./gradlew bootJar --no-daemon -x test

# ──────────────────────────────────────────────────────────────────────────────
# 3단계: 최종 이미지 (프론트엔드 + 백엔드)
# ──────────────────────────────────────────────────────────────────────────────
FROM --platform=linux/amd64 eclipse-temurin:17-jre
WORKDIR /app

# 프론트엔드 빌드 결과를 static 디렉토리로 복사
COPY --from=frontend-build /app/frontend/dist ./static

# 백엔드 JAR 복사
COPY --from=backend-build /app/build/libs/*-SNAPSHOT.jar app.jar

# 포트 노출
EXPOSE 8080

# 백엔드 실행 (Asia/Seoul 타임존 설정)
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
