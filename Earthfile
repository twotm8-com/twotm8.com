VERSION 0.7

llvm-base:
    FROM eclipse-temurin:17-focal
    COPY scripts /scripts
    RUN /scripts/setup-debian.sh 14

deps:
    FROM +llvm-base
    WORKDIR /sources
    
    # Coursier and SBT
    RUN curl -fL -o /bin/cs https://github.com/coursier/launchers/raw/master/coursier && \
        chmod +x /bin/cs 
    RUN sbt --sbt-create version

    # VCPKG dependencies
    COPY vcpkg.json /sources/
    RUN apt install ninja-build
    ENV CC "/usr/lib/llvm-14/bin/clang"
    ENV CXX "/usr/lib/llvm-14/bin/clang++"
    ENV VCPKG_FORCE_SYSTEM_BINARIES "true"
    RUN cs launch com.indoorvivants.vcpkg:sn-vcpkg_3:0.0.11 -- install-manifest vcpkg.json -l -c
    
    # SBT dependencies
    COPY build.sbt /sources
    COPY project/*.sbt /sources/project/
    RUN sbt vcpkgInstall update

app:
    FROM +deps
    WORKDIR /sources
    COPY . /sources

    ENV LLVM_BIN "/usr/lib/llvm-14/bin"
    ENV CC "/usr/lib/llvm-14/bin/clang"
    ENV SN_RELEASE "fast"
    ENV CI "true"

    RUN sbt clean buildApp
    SAVE ARTIFACT build/twotm8 /build/twotm8
    SAVE ARTIFACT build/static /build/static

docker:
    FROM nginx/unit:1.29.1-minimal
    ARG ver=latest

    COPY +app/build/twotm8 /usr/bin/twotm8
    COPY +app/build/static/* /www/static/
    COPY config.json /docker-entrypoint.d/config.json

    EXPOSE 8080
    CMD ["unitd", "--no-daemon", "--control", "unix:/var/run/control.unit.sock", "--log", "/dev/stderr"]
    SAVE IMAGE twotm8:$ver

