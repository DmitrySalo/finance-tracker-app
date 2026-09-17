FROM gradle:9.1.0-jdk21-alpine AS build

WORKDIR /workspace

COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY --chown=gradle:gradle backend backend
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget \
    && addgroup --system application \
    && adduser --system --ingroup application application

WORKDIR /app

COPY --from=build /workspace/build/libs/finance-tracker-app-0.0.1-SNAPSHOT.jar app.jar

USER application

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
