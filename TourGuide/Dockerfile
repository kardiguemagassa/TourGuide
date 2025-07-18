FROM eclipse-temurin:17-jre-alpine

ARG JAR_FILE=target/tourguide-0.0.1-SNAPSHOT.jar

WORKDIR /opt/app

COPY ${JAR_FILE} tourguide-0.0.1-SNAPSHOT.jar
COPY entrypoint.sh entrypoint.sh

RUN chmod 755 entrypoint.sh

ENTRYPOINT ["./entrypoint.sh"]

