FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app \
	&& useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
	&& mkdir -p /data/media \
	&& chown -R app:app /app /data
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC" \
	SPRING_PROFILES_ACTIVE=prod \
	MEDIA_ROOT_DIR=/data/media
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
