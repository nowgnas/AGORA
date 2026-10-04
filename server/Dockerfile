FROM eclipse-temurin:11-jdk AS builder
COPY server/gradlew .
COPY server/gradle gradle
COPY server/build.gradle .
COPY server/settings.gradle .
COPY server/src src
RUN chmod +x ./gradlew
RUN ./gradlew clean compileQuerydsl
RUN ./gradlew clean bootJar

FROM eclipse-temurin:11-jre
COPY --from=builder build/libs/*.jar app.jar

# 실행에 필요한 값(DB, JWT, Redis, OpenVidu, OAuth, GCP 키 경로)은 이미지에 넣지 않고 런타임 환경변수로 주입한다.
ENV USE_PROFILE=prod
EXPOSE 8081
ENTRYPOINT ["java","-jar","-Dspring.profiles.active=${USE_PROFILE}", "-Ddatabase.hostname=${HOST_NAME}", "-Ddatabase.schema=${SCHEMA}", "-Ddatabase.username=${USERNAME}", "-Ddatabase.password=${PASSWORD}", "-Djwt.secret=${JWT_SECRET}", "-Dredishost=${REDISHOST}", "-Dredispass=${REDISPASS}", "-Dopenvidu.hostname=${MEETING_HOST}", "-Dopenvidu.secret=${MEETING_SECRET}", "/app.jar"]
