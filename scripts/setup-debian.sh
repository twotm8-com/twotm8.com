#!/usr/bin/sh

LLVM_VERSION=$1

apt update && apt install -y curl && \
    curl -Lo /usr/local/bin/sbt https://raw.githubusercontent.com/sbt/sbt/1.8.x/sbt && \
    chmod +x /usr/local/bin/sbt && \
    curl -Lo llvm.sh https://apt.llvm.org/llvm.sh && \
    chmod +x llvm.sh && \
    apt install -y lsb-release wget software-properties-common gnupg autopoint libtool && \
    curl -Lo kitware-archive.sh https://apt.kitware.com/kitware-archive.sh && \
    chmod +x kitware-archive.sh &&\
    ./kitware-archive.sh && \
    apt update && \
    apt install -y cmake ninja-build zip unzip tar make autoconf pkg-config git && \
    ./llvm.sh $LLVM_VERSION && \
    # install Unit, OpenSSL and Unit development headers
    curl --output /usr/share/keyrings/nginx-keyring.gpg  \
      https://unit.nginx.org/keys/nginx-keyring.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/ubuntu/ $(lsb_release -c -s) unit" >> /etc/apt/sources.list.d/unit.list && \
    echo "deb-src [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/ubuntu/ $(lsb_release -c -s) unit" >> /etc/apt/sources.list.d/unit.list && \
    apt update && \
    apt install -y unit-dev
