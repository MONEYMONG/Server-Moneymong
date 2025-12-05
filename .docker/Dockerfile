FROM eclipse-temurin:17-jdk
ARG JAR_FILE=build/libs/moneymong-0.0.1.jar
ENV ACTIVE_PROFILE=${ACTIVE_PROFILE} \
DB_URL=${DB_URL} \
REDIS_PASSWORD=${REDIS_PASSWORD}

COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=${ACTIVE_PROFILE}", "-jar","/app.jar"]
