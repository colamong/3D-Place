# ---------- Build stage ----------
FROM amazoncorretto:21-alpine AS build
WORKDIR /workspace
ENV GRADLE_USER_HOME=/home/gradle/.gradle          
RUN apk add --no-cache bash curl unzip zip tar findutils 
# Gradle/설정
COPY gradle ./gradle
COPY gradlew .
RUN sed -i "s/\r$//" gradlew && chmod +x gradlew
COPY settings.gradle.kts .
COPY build.gradle.kts .

# 모듈별 gradle.kts만 먼저 복사해서 의존성 캐시
COPY services/world/build.gradle.kts services/world/
COPY common/*/build.gradle.kts      common/

COPY contracts/*/build.gradle.kts   contracts/
# 윈도 줄바꿈 대비

# 의존성만 먼저 내려 캐시층 확보
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew :services:world:dependencies --no-daemon

# 실제 소스 복사
COPY services/world/src services/world/src
COPY common               common

COPY contracts            contracts
# world 빌드
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew :services:world:bootJar --no-daemon -x test -Dspring.flyway.enabled=false && \
    cp services/world/build/libs/*.jar /tmp/app.jar

# ---------- Run stage ----------
FROM amazoncorretto:21-alpine
RUN addgroup -g 10001 -S app || true && adduser -u 10001 -S -D -H -G app app || true
WORKDIR /app
COPY --from=build /tmp/app.jar /app/app.jar
EXPOSE 8083
USER 10001:10001
ENTRYPOINT ["java","-XX:+UseG1GC","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]





