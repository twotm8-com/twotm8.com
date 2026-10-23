FROM eclipse-temurin:17-noble AS builder

COPY scripts /scripts

RUN /scripts/setup-debian.sh

ENV SN_RELEASE="fast"
ENV CI="true"

RUN curl -fL -o /bin/cs https://github.com/coursier/launchers/raw/master/coursier && \
    chmod +x /bin/cs && cs --version

RUN curl -fL -o /bin/sbt https://raw.githubusercontent.com/sbt/sbt/v1.13.0/sbt && \
    chmod +x /bin/sbt && sbt -V --sbt-create    

COPY vcpkg.json /sources/

RUN cs launch com.indoorvivants.vcpkg:sn-vcpkg_3:0.0.21 -- install --manifest /sources/vcpkg.json -v

COPY . /sources

RUN cd /sources && sbt clean buildApp

# The executable embeds unitd, which needs OpenSSL 3 and PCRE2
FROM debian:bookworm-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends libssl3 libpcre2-8-0 ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /sources/build/twotm8 /usr/bin/twotm8
COPY --from=builder /sources/build/ /www/static

EXPOSE 8080

CMD ["/usr/bin/twotm8"]
