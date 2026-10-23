FROM debian:bookworm-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends libssl3 libpcre2-8-0 ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY build/twotm8 /usr/bin/twotm8
COPY build /www/static

EXPOSE 8080

CMD ["/usr/bin/twotm8"]
