# ------- build -------
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ------- runtime -------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/payment-service-*.jar app.jar

RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser
USER appuser

EXPOSE 8095
ENTRYPOINT ["java","-jar","/app/app.jar"]