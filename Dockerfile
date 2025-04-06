# Stage 1: Build
FROM maven:3.9.0-eclipse-temurin AS build

WORKDIR /app

# Copy and resolve dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the packaged application from the build stage
COPY --from=build /app/target/chat-0.0.1-SNAPSHOT.jar .

EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "/app/chat-0.0.1-SNAPSHOT.jar"]
