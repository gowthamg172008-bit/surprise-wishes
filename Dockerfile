FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src/main/java/app/Main.java src/main/java/app/Main.java
RUN mkdir -p build/classes \
    && javac --add-modules jdk.httpserver -encoding UTF-8 -d build/classes src/main/java/app/Main.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/classes /app/build/classes
COPY index.html style.css app.js /app/
ENV APP_DIR=/app
ENV DATA_DIR=/var/data
EXPOSE 8080
CMD ["java", "--add-modules", "jdk.httpserver", "-cp", "/app/build/classes", "app.Main"]
