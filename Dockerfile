# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY config config
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system ecommerce \
    && useradd --system --gid ecommerce --home-dir /app --shell /usr/sbin/nologin ecommerce

WORKDIR /app

COPY --from=build /workspace/target/ECommerceAPI-0.0.1-SNAPSHOT.jar /app/app.jar

USER ecommerce

EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
