FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY owlmask-share ./owlmask-share
RUN mvn -f owlmask-share/pom.xml -q -DskipTests install
COPY owlmask-pdf ./owlmask-pdf
RUN mvn -f owlmask-pdf/pom.xml -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --create-home owlmask
WORKDIR /app
COPY --from=build /workspace/owlmask-pdf/target/owlmask-pdf-*.jar /app/owlmask-pdf.jar
USER 10001
EXPOSE 9070
ENTRYPOINT ["java", "-jar", "/app/owlmask-pdf.jar"]
