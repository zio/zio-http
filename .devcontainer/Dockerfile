# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.238.1/containers/java/.devcontainer/base.Dockerfile

# [Choice] Java version (use -bullseye variants on local arm64/Apple Silicon): 11, 17, 11-bullseye, 17-bullseye, 11-buster, 17-buster
ARG VARIANT="11"
FROM mcr.microsoft.com/vscode/devcontainers/java:0-${VARIANT}


RUN curl -s "https://get.sdkman.io" | bash

# Install Scala Lang
ARG SBT_VERSION="1.7.1"
RUN \
  curl -L "https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz" | tar zxf - -C /usr/share  && \
  cd /usr/share/sbt/bin && \
  rm sbt.bat sbtn-x86_64-apple-darwin sbtn-x86_64-pc-linux sbtn-x86_64-pc-win32.exe && \
  ln -s /usr/share/sbt/bin/sbt /usr/local/bin/sbt

ARG SCALA_VERSION="3.1.3"
RUN \
  mkdir /setup-project && \
  cd /setup-project && \
  echo "scalaVersion := \"${SCALA_VERSION}\"" > build.sbt && \
  echo "case object Temp" > Temp.scala && \
  sbt compile && \
  rm -rf /setup-project

CMD ["sbt"]
