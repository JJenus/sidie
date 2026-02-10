# ---------- build stage ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build

# cache dependencies first
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline

# copy source and build
COPY src ./src
RUN mvn -q -DskipTests clean package


# ---------- runtime stage ----------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# smaller image, only jar
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]