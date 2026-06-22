FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY core/build.gradle core/build.gradle
COPY memory/build.gradle memory/build.gradle
COPY ecommerce/build.gradle ecommerce/build.gradle
COPY agents/build.gradle agents/build.gradle
COPY ecommerce-mcp/build.gradle ecommerce-mcp/build.gradle
COPY admin/build.gradle admin/build.gradle
COPY api/build.gradle api/build.gradle

# Download dependencies first (cached layer)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY core/src core/src
COPY memory/src memory/src
COPY ecommerce/src ecommerce/src
COPY agents/src agents/src
COPY admin/src admin/src
COPY api/src api/src

RUN ./gradlew :api:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/api/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
