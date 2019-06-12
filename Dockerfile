FROM anapsix/alpine-java:8_server-jre

ARG VERSION
COPY target/atomix-log-protocol.tar.gz /root/atomix-log-protocol.tar.gz
RUN tar -xvf /root/atomix-log-protocol.tar.gz -C /root && \
    mv /root/atomix-log-protocol /root/atomix && \
    chmod u+x /root/atomix/bin/atomix-server && \
    rm /root/atomix-log-protocol.tar.gz

WORKDIR /root/atomix

EXPOSE 5678

ENTRYPOINT ["./bin/atomix-server"]
