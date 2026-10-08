# ── Etapa 1: Build ──────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copia solo el pom primero (cachea dependencias si no cambia)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copia el código fuente y compila
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Etapa 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Usuario no-root por seguridad
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Copia el JAR desde la etapa de build
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 9200

ENTRYPOINT ["java", "-jar", "app.jar"]