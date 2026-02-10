FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build

# copy everything (pom.xml + src)
COPY . .

RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]