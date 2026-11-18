# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /build
COPY backend/pom.xml backend/mvnw ./
COPY backend/.mvn/ .mvn/
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -ntp dependency:go-offline
COPY backend/src/main/ src/main/
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080'

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
