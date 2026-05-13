FROM docker.io/library/node:22 AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npx vite build --outDir /static --emptyOutDir

FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
COPY --from=web /static app/src/main/resources/static
RUN ./mvnw -B -DskipTests package

FROM docker.io/library/eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/app/target/app-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
