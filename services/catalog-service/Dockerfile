FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
VOLUME /tmp
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
