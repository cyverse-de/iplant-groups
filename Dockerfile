FROM clojure:openjdk-17-lein-alpine

WORKDIR /usr/src/app

RUN apk add --no-cache git

RUN ln -s "/opt/openjdk-17/bin/java" "/bin/iplant-groups"

ENV OTEL_TRACES_EXPORTER none

COPY project.clj /usr/src/app/
RUN lein deps

COPY conf/main/logback.xml /usr/src/app/
COPY . /usr/src/app

RUN lein do clean, uberjar && \
    cp target/iplant-groups-standalone.jar .

ENTRYPOINT ["iplant-groups", "-Dlogback.configurationFile=/etc/iplant/de/logging/iplant-groups-logging.xml", "-javaagent:/usr/src/app/opentelemetry-javaagent.jar", "-Dotel.resource.attributes=service.name=iplant-groups", "-cp", ".:iplant-groups-standalone.jar:/", "iplant_groups.core"]
CMD ["--help"]

ARG git_commit=unknown
ARG version=unknown
ARG descriptive_version=unknown

LABEL org.cyverse.git-ref="$git_commit"
LABEL org.cyverse.version="$version"
LABEL org.cyverse.descriptive-version="$descriptive_version"
LABEL org.label-schema.vcs-ref="$git_commit"
LABEL org.label-schema.vcs-url="https://github.com/cyverse-de/iplant-groups"
LABEL org.label-schema.version="$descriptive_version"
