FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN ./mvnw -pl ${MODULE} -am -B -DskipTests package

FROM eclipse-temurin:21-jre
ARG MODULE
ARG PORT=8080
ENV PORT=${PORT}
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/ /app/
RUN if [ -f "/app/${MODULE}-0.0.1-SNAPSHOT-exec.jar" ]; then \
      mv "/app/${MODULE}-0.0.1-SNAPSHOT-exec.jar" /app/app.jar; \
    else \
      mv "/app/${MODULE}-0.0.1-SNAPSHOT.jar" /app/app.jar; \
    fi
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
