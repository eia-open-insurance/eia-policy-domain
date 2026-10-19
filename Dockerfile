FROM eclipse-temurin:22-jre

WORKDIR /app
COPY build/libs/eia-policy-domain-*-SNAPSHOT.jar app.jar

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
