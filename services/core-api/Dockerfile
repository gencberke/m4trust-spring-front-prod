# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system m4trust && useradd --system --gid m4trust --no-create-home m4trust

WORKDIR /app
COPY --from=build /workspace/target/core-api-*.jar app.jar

RUN chown -R m4trust:m4trust /app
USER m4trust

# Runtime port is provided by the environment (e.g. Railway); no port is
# baked into the image or the architecture. 8080 is only a local default.
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
