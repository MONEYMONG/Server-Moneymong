# 태그가 갱신돼도 서버가 베이스 레이어를 매번 다시 받지 않도록 digest 고정
FROM eclipse-temurin:17-jre@sha256:c6f2875c05ea10f16398bdc5f73405c384991506f2f1ead8bcc6582ae8adea79
ARG JAR_FILE=build/libs/moneymong-0.0.1.jar
ENV ACTIVE_PROFILE=${ACTIVE_PROFILE} \
DB_URL=${DB_URL} \
REDIS_PASSWORD=${REDIS_PASSWORD}

COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=${ACTIVE_PROFILE}", "-jar","/app.jar"]
