# Stage 1: build the jar with the full JDK and Maven wrapper
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy only the build files first so dependency downloads are cached between code changes
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (they need Docker for Testcontainers), so the image build skips them
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar app.jar

# Stage 2: small runtime image with only a JRE and the jar
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S liftlog && adduser -S liftlog -G liftlog
USER liftlog

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/v1/health || exit 1

# Respect container memory limits instead of the host's RAM
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
