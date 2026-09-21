FROM jenkins/jenkins:lts-jdk21

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends awscli ca-certificates \
    && rm -rf /var/lib/apt/lists/*

USER jenkins
