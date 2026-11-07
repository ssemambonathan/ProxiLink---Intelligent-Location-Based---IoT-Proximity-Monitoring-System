# Multi-stage build keeps the runtime image small and free of the Gradle cache.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S proxilink && adduser -S proxilink -G proxilink
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN mkdir -p /app/data && chown -R proxilink:proxilink /app
USER proxilink
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
