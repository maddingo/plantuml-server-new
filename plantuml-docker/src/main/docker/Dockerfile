FROM eclipse-temurin:21-jdk-jammy as build

USER root
RUN apt-get update && \
    apt-get install -y --no-install-recommends graphviz fonts-noto-cjk && \
    apt-get clean && rm -rf /var/lib/apt/lists/* && \
    useradd -r -s /bin/false spring && \
    mkdir -p /opt/spring && \
    chown spring:spring /opt/spring

USER spring

ENV GRAPHVIZ_DOT=/usr/bin/dot

COPY plantuml-web.jar /opt/spring/plantuml-web.jar
WORKDIR /opt/spring

ENTRYPOINT ["java", "-jar", "plantuml-web.jar"]
