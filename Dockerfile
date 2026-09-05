FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress -Dfrontend.skip=true dependency:go-offline

COPY src ./src
COPY config ./config
RUN mvn --batch-mode --no-transfer-progress -Dfrontend.skip=true clean package

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/target/data-agent-*.jar /app/data-agent.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/data-agent.jar"]
