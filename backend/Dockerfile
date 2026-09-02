# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 의존성 해결을 별도 레이어로 굳힌다. pom.xml 이 그대로면 CI 의 레이어 캐시가
# 이 단계를 통째로 건너뛰므로, 소스만 고친 배포에서 매번 재다운로드하지 않는다.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests clean package

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home /app app

COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

# curl 을 깔지 않으려고 bash 내장 /dev/tcp 로 확인한다.
# /dev/tcp 는 bash 전용이라 sh(dash) 로 실행하면 동작하지 않는다.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]

# 한글 파일명·엑셀 내용이 깨지지 않도록 인코딩과 타임존을 고정한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Dfile.encoding=UTF-8", "-Duser.timezone=Asia/Seoul", "-jar", "/app/app.jar"]
