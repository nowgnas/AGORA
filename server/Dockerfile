FROM adoptopenjdk:11-hotspot AS builder
# env 
ENV USE_PROFILE dev
ENV HOST_NAME localhost
ENV SCHEMA localagora
ENV USERNAME root
ENV PASSWORD 1234
ENV JWT_SECRET slfjlskdfjslkf
ENV REDISHOST AGORA
ENV REDISPASS redispass
ENV MEETING_HOST HOST
ENV MEETING_SECRET SECRET
# env finish
COPY server/gradlew .
COPY server/gradle gradle
COPY server/build.gradle .
COPY server/settings.gradle .
COPY server/src src
RUN chmod +x ./gradlew
RUN ./gradlew clean compileQuerydsl
RUN ./gradlew clean bootJar

FROM adoptopenjdk:11-hotspot
COPY --from=builder build/libs/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java","-jar","-Dspring.profiles.active=${USE_PROFILE}", "-Ddatabase.hostname=${HOST_NAME}", "-Ddatabase.schema=${SCHEMA}", "-Ddatabase.username=${USERNAME}", "-Ddatabase.password=${PASSWORD}", "-Djwt.secret=${JWT_SECRET}", "-Dredishost=${REDISHOST}", "-Dredispass=${REDISPASS}", "-Dopenvidu.hostname=${MEETING_HOST}", "-Dopenvidu.secret=${MEETING_SECRET}", "/app.jar"]
