# Etapa 1: build com Maven (roda os testes do filtro)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

# Etapa 2: imagem final só com a JRE
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app && mkdir -p /app/logs && chown app /app/logs
COPY --from=build /build/target/oraculo-tutor-1.0.0.jar app.jar
USER app
ENV ARQUIVO_LOG=/app/logs/conversas.jsonl \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
