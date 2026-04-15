FROM ghcr.io/graalvm/native-image-community:25 AS builder
RUN microdnf install -y maven findutils
WORKDIR /build/backend
COPY backend/pom.xml .
RUN mvn dependency:go-offline -q
COPY backend/src ./src
COPY skills /build/skills
ENV SPRING_PROFILES_ACTIVE=prod
RUN mvn -Pnative native:compile -DskipTests -q

FROM debian:12-slim
WORKDIR /app
COPY --from=builder /build/backend/target/training-planner-backend ./app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["./app"]
