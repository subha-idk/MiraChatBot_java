# --- Stage 1: Build the application ---
# Use a Maven image with Java 17 to build the project
FROM maven:3.8-openjdk-17 AS build

# Set the working directory inside the container
WORKDIR /app

# Copy the pom.xml file and download dependencies
# This is done separately to leverage Docker's layer caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the rest of the source code
COPY src ./src

# Package the application, skipping tests
RUN mvn package -DskipTests


# --- Stage 2: Create the final, lightweight image ---
# Use a minimal Java 17 runtime image
FROM eclipse-temurin:17-jre-jammy

# Set the working directory
WORKDIR /app

# Copy the executable .jar file from the build stage
# The path inside target/ might vary slightly, so we use a wildcard
COPY --from=build /app/target/MiraBot-*.jar app.jar

# Expose the port the application runs on
EXPOSE 8080

# The command to run the application when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]