FROM discoenv/clojure-base:master

ENV CONF_TEMPLATE=/usr/src/app/iplant-groups.properties.tmpl
ENV CONF_FILENAME=iplant-groups.properties
ENV PROGRAM=iplant-groups

VOLUME ["/etc/iplant/de"]

COPY project.clj /usr/src/app/
RUN lein deps

COPY conf/main/logback.xml /usr/src/app/
COPY . /usr/src/app

RUN lein uberjar && \
    cp target/iplant-groups-standalone.jar .

RUN ln -s "/usr/bin/java" "/bin/iplant-groups"

ENTRYPOINT ["run-service", "-Dlogback.configurationFile=/etc/iplant/de/logging/iplant-groups-logging.xml", "-cp", ".:iplant-groups-standalone.jar:/", "iplant-groups.core"]

ARG git_commit=unknown
ARG version=unknown
ARG descriptive_version=unknown

LABEL org.cyverse.git-ref="$git_commit"
LABEL org.cyverse.version="$version"
LABEL org.cyverse.descriptive-version="$descriptive_version"
LABEL org.label-schema.vcs-ref="$git_commit"
LABEL org.label-schema.vcs-url="https://github.com/cyverse-de/iplant-groups"
LABEL org.label-schema.version="$descriptive_version"
