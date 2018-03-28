# Dockerfile to build container for unit testing

FROM openjdk:9

RUN apt-get update && apt-get install -y git ant libgfortran3

WORKDIR /root

ADD . ./

ENTRYPOINT ant test
