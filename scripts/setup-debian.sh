#!/usr/bin/sh

apt update && apt install -y lsb-release zip unzip tar && \
curl --output /usr/share/keyrings/nginx-keyring.gpg  \
  https://unit.nginx.org/keys/nginx-keyring.gpg && \
echo "deb [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/ubuntu/ $(lsb_release -c -s) unit" >> /etc/apt/sources.list.d/unit.list && \
echo "deb-src [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/ubuntu/ $(lsb_release -c -s) unit" >> /etc/apt/sources.list.d/unit.list && \
apt update && \
apt install -y unit-dev
