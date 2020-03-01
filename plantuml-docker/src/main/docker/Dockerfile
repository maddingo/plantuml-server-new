FROM jetty:${jetty.version}-jre11

USER root
RUN apt-get update && \
    apt-get install -y --no-install-recommends graphviz fonts-noto-cjk && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

USER jetty

ENV GRAPHVIZ_DOT=/usr/bin/dot

ARG CONTEXT_NAME=${dockerfile.webcontext}

COPY plantuml-web.war /var/lib/jetty/webapps/$CONTEXT_NAME.war
