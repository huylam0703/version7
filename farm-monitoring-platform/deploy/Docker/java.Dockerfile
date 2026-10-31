# syntax=docker/dockerfile:1
ARG JAVA_VERSION=21

FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
ARG SERVICE_PATH
ARG PLATFORM_PATH=platform/java
WORKDIR /repo

# 1) Hợp đồng OpenAPI (farm-contracts sinh code từ đây) + platform Java
COPY contracts ./contracts
COPY ${PLATFORM_PATH} ./${PLATFORM_PATH}
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -f ${PLATFORM_PATH}/pom.xml -DskipTests install

# 2) Service (giữ nguyên cấu trúc thư mục như repo)
COPY ${SERVICE_PATH} ./${SERVICE_PATH}
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -f ${SERVICE_PATH}/pom.xml -DskipTests package

# ---------- runtime ----------
FROM eclipse-temurin:${JAVA_VERSION}-jre
ARG SERVICE_PATH
WORKDIR /app
COPY --from=build /repo/${SERVICE_PATH}/target/*.jar app.jar

ENV SERVER_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]