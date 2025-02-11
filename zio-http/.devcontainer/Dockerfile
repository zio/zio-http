# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.238.1/containers/java/.devcontainer/base.Dockerfile

# [Choice] Java version (use -bullseye variants on local arm64/Apple Silicon): 11, 17, 11-bullseye, 17-bullseye, 11-buster, 17-buster
ARG VARIANT="17"
FROM mcr.microsoft.com/vscode/devcontainers/java:${VARIANT}


RUN curl -s "https://get.sdkman.io" | bash

# Install Scala Lang
ARG SBT_VERSION="1.10.1"
RUN \
  curl -L "https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz" | tar zxf - -C /usr/share  && \
  cd /usr/share/sbt/bin && \
  ln -s /usr/share/sbt/bin/sbt /usr/local/bin/sbt

ARG SCALA_VERSION="3.3.3"
RUN \
  mkdir /setup-project && \
  cd /setup-project && \
  echo "scalaVersion := \"${SCALA_VERSION}\"" > build.sbt && \
  echo "case object Temp" > Temp.scala && \
  sbt compile && \
  rm -rf /setup-project

RUN \
  mkdir /setup-wrk && \
  sudo apt-get update -y && sudo apt-get install build-essential libssl-dev git -y && \
  git clone https://github.com/wg/wrk.git wrk && \
  cd wrk && \
  make && \
  cp wrk /usr/local/bin && \
  rm -rf /setup-wrk

CMD ["sbt"]
