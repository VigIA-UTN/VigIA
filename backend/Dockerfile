# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Run the API
FROM eclipse-temurin:17-jre AS api
WORKDIR /app
COPY --from=build /app/backend-api/target/backend-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# ExitOnOutOfMemoryError: sin memoria, la JVM sigue viva pero con los hilos de Tomcat muertos y el pool tomado, y
# no responde más; saliendo, Docker/Railway la reinician (ver MEMORY.md).
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Stage 3: Run the Worker
FROM eclipse-temurin:17-jre AS worker
WORKDIR /app
COPY --from=build /app/backend-worker/target/backend-worker-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
# ExitOnOutOfMemoryError: sin memoria, la JVM sigue viva pero con los hilos de Tomcat muertos y el pool tomado, y
# no responde más; saliendo, Docker/Railway la reinician (ver MEMORY.md).
ENV JAVA_OPTS="-Xmx128m -Xms64m -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
