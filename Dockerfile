# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy@sha256:9d8dcf999b0bce2453e913823595a5ff2a4e8e9e5d5241b45280d0ff069818ec AS build

WORKDIR /workspace

# Keep the Linux Maven wrapper path on the configured ZIP distribution so
# distributionSha256Sum verifies the same artifact on Windows, CI, and Docker builds.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip=6.0-26ubuntu3.2 \
    && rm -rf /var/lib/apt/lists/*

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY config config
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy@sha256:d63bd8d9b171999cbed8576f2c76e874dd4856791a358536e5c4d407e77edc13

RUN groupadd --system ecommerce \
    && useradd --system --gid ecommerce --home-dir /app --shell /usr/sbin/nologin ecommerce

WORKDIR /app

COPY --from=build /workspace/target/ecommerce-api-0.0.1-SNAPSHOT.jar /app/app.jar

USER ecommerce

EXPOSE 8080

ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE=prod
ENV APP_ENVIRONMENT=prod
ENV APP_CONTAINERIZED=true
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
