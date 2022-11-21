FROM eclipse-temurin:17-focal as builder

COPY scripts /scripts

RUN /scripts/setup-debian.sh

ENV LLVM_BIN "/usr/lib/llvm-13/bin"
ENV CC "/usr/lib/llvm-13/bin/clang"
ENV SN_RELEASE "fast"
ENV CI "true"

RUN curl -fL -o /bin/cs https://github.com/coursier/launchers/raw/master/coursier && \
    chmod +x /bin/cs && cs --version

COPY vcpkg.json /sources/

RUN cs launch com.indoorvivants.vcpkg:scala-vcpkg_3:0.0.8 -- install-manifest /sources/vcpkg.json -v

COPY . /sources

RUN cd /sources && sbt clean buildApp

FROM nginx/unit:1.28.0-minimal as runtime_deps

FROM runtime_deps

COPY --from=builder /sources/build/twotm8 /usr/bin/twotm8
COPY --from=builder /sources/build/ /www/static

COPY config.json /docker-entrypoint.d/config.json

RUN chmod 0777 /usr/bin/twotm8

EXPOSE 8080

CMD ["unitd", "--no-daemon", "--control", "unix:/var/run/control.unit.sock", "--log", "/dev/stderr"]
