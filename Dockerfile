FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

# Install LaTeX packages for local PDF compilation
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        texlive-latex-base \
        texlive-latex-extra \
        texlive-fonts-recommended && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/jobtracker-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]