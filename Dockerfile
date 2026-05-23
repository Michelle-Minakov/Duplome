# ── Крок 1: збираємо JAR ──────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Завантажуємо залежності окремо — кешується поки pom.xml не змінився
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Крок 2: мінімальний runtime-образ ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /build/target/doc-generator-1.0-SNAPSHOT.jar app.jar

RUN mkdir -p /app/generated /app/data

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
