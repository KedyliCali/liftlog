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

# Stage 2: small runtime image with only a JRE and the app
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# WORKDIR creates /app as root; the app user needs to write the unpacked jar and the archive into it
RUN addgroup -S liftlog && adduser -S liftlog -G liftlog && chown liftlog:liftlog /app
USER liftlog

# Unpacked jar (app.jar + lib/): class data sharing only works with classes loaded from plain jars,
# not from the nested jars inside a Spring Boot fat jar
COPY --from=build --chown=liftlog:liftlog /workspace/app.jar /tmp/app.jar
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app/application && rm /tmp/app.jar

# Startup on Render's free 0.1 CPU took about 3 minutes, so the first request after the API slept waited
# that long. Two things cut it (measured with docker run --cpus 0.1, see docs/DEPLOY.md):
# - TieredStopAtLevel=1: only the fast C1 compiler, so less CPU goes into compiling during startup.
#   Peak speed is a bit lower, which this small API never notices.
# - A class data sharing archive: one training start during the image build records the loaded classes,
#   and real starts map them from app.jsa instead of parsing and verifying them again.
# The training start stops right after the Spring context is ready (spring.context.exit=onRefresh) and
# must not touch a database, which does not exist at build time: Flyway is off, Hibernate is told the
# dialect instead of asking the database, and the secrets are throwaway values.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
RUN java $JAVA_OPTS -XX:ArchiveClassesAtExit=application/app.jsa -Dspring.context.exit=onRefresh \
        -Dspring.flyway.enabled=false \
        -Dspring.jpa.hibernate.ddl-auto=none \
        -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -DJWT_SECRET=build-time-training-secret-not-used-anywhere-else \
        -DDB_PASSWORD=unused \
        -jar application/app.jar

EXPOSE 8080
# PORT is set by hosting platforms such as Render; locally it falls back to 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:${PORT:-8080}/api/v1/health || exit 1

# Heap gets 65% of the container limit (Render free = 512 MB) and the rest is left for metaspace and
# threads; SerialGC has the lowest memory overhead on 1 CPU. sh -c expands JAVA_OPTS; exec keeps java
# as PID 1 so it receives Render's stop signal directly.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:SharedArchiveFile=application/app.jsa -jar application/app.jar"]
