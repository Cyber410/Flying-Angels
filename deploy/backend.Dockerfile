FROM eclipse-temurin:21-jre

WORKDIR /app
COPY backend/target/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080'

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
