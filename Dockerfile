FROM node:22-bookworm-slim AS frontend
WORKDIR /workspace
COPY package.json package-lock.json tsconfig.json ./
RUN npm ci --no-audit --no-fund
COPY frontend ./frontend
RUN npm run build

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradle ./gradle
COPY gradlew build.gradle settings.gradle gradle.properties ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src/main ./src/main
COPY --from=frontend /workspace/src/main/resources/static/js ./src/main/resources/static/js
RUN ./gradlew --no-daemon bootJar -x compileFrontend

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --home-dir /app app
COPY --from=build --chown=10001:10001 /workspace/build/libs/*.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=10000 \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k -XX:ActiveProcessorCount=1 -Dfile.encoding=UTF-8"
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
