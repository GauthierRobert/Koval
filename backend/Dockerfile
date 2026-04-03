FROM ghcr.io/graalvm/native-image-community:25 AS builder
RUN microdnf install -y maven findutils
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
ENV SPRING_PROFILES_ACTIVE=prod
RUN mvn -Pnative native:compile -DskipTests -q

FROM debian:12-slim
WORKDIR /app
COPY --from=builder /app/target/training-planner-backend ./app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["./app"]
