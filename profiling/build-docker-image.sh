#!/bin/bash
COMMIT_SHA=$(git rev-parse --short HEAD)
TAG="zio-http:$COMMIT_SHA"

if [[ $(uname -m) == 'arm64' ]]; then
    export ARCH="linux-arm-64"
else
    export ARCH="linux-x86-64"
fi

if [ ! -e "/var/run/docker.sock" ]; then
    echo "'/var/run/docker.sock' does not exist.  Are you sure Docker is running?"
    exit 1
fi

mkdir -p src/main/scala
rm ./src/main/scala/Main.scala
cp ../zio-http-example/src/main/scala/example/PlainTextBenchmarkServer.scala ./src/main/scala/Main.scala

cd "$(dirname "$0")/../"
docker build -f ./profiling/Dockerfile --build-arg ARCH=$ARCH -t $TAG .
docker tag $TAG zio-http:latest
