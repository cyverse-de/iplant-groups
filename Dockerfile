FROM clojure:alpine

RUN apk add --update git && \
    rm -rf /var/cache/apk

VOLUME ["/etc/iplant/de"]

ARG git_commit=unknown
ARG version=unknown
LABEL org.iplantc.de.iplant-groups.git-ref="$git_commit" \
      org.iplantc.de.iplant-groups.version="$version"

COPY . /usr/src/app
COPY conf/main/logback.xml /usr/src/app/logback.xml

WORKDIR /usr/src/app

RUN lein uberjar && \
    cp target/iplant-groups-standalone.jar .

RUN ln -s "/usr/bin/java" "/bin/iplant-groups"

ENTRYPOINT ["iplant-groups", "-Dlogback.configurationFile=/etc/iplant/de/logging/iplant-groups-logging.xml", "-cp", ".:iplant-groups-standalone.jar:/", "iplant_groups.core"]
CMD ["--help"]
