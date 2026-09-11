FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 10001 --no-create-home appuser

COPY target/*.jar app.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
