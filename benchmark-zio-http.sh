#!/bin/sh
set -e

COMMIT_SHA=$(git rev-parse --short HEAD)
ZIO_HTTP="zio/zio-http.git#$COMMIT_SHA"

if [ ! -e "/var/run/docker.sock" ]; then
    echo "'/var/run/docker.sock' does not exist.  Are you sure Docker is running?"
    exit 1
fi

if [ ! -d "../FrameworkBenchMarks" ]; then
    git clone https://github.com/zio/FrameworkBenchmarks.git ../FrameworkBenchMarks
fi

rm ../FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.scala
cp ./zio-http-example/src/main/scala/example/PlainTextBenchmarkServer.scala ../FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.scala
cd ../FrameworkBenchMarks

if [ -f "frameworks/Scala/zio-http/build.sbt.bak" ]; then
    # if a backup (ie an original version exists), restore it, it's useful if you want to benchmark multiple commits in a row
    mv frameworks/Scala/zio-http/build.sbt.bak frameworks/Scala/zio-http/build.sbt
fi

# this is compatible with both GNU and BSD sed
sed -i.bak "s|---COMMIT_SHA---|${ZIO_HTTP}|g" frameworks/Scala/zio-http/build.sbt
./tfb --test zio-http | tee result
RESULT_REQUEST=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+")
RESULT_CONCURRENCY=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "concurrency: [0-9]+")
echo ::set-output name=request_result::$(echo $RESULT_REQUEST)
echo ::set-output name=concurrency_result::$(echo $RESULT_CONCURRENCY)
RESULT_REQUEST=$(echo $(grep -B 1 -A 17 "Concurrency: 1024 for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+")
RESULT_CONCURRENCY=$(echo $(grep -B 1 -A 17 "Concurrency: 1024 for plaintext" result) | grep -oiE "concurrency: [0-9]+")
echo ::set-output name=request_result::$(echo $RESULT_REQUEST)
echo ::set-output name=concurrency_result::$(echo $RESULT_CONCURRENCY)
RESULT_REQUEST=$(echo $(grep -B 1 -A 17 "Concurrency: 4096 for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+")
RESULT_CONCURRENCY=$(echo $(grep -B 1 -A 17 "Concurrency: 4096 for plaintext" result) | grep -oiE "concurrency: [0-9]+")
echo ::set-output name=request_result::$(echo $RESULT_REQUEST)
echo ::set-output name=concurrency_result::$(echo $RESULT_CONCURRENCY)
RESULT_REQUEST=$(echo $(grep -B 1 -A 17 "Concurrency: 16384 for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+")
RESULT_CONCURRENCY=$(echo $(grep -B 1 -A 17 "Concurrency: 16384 for plaintext" result) | grep -oiE "concurrency: [0-9]+")
echo ::set-output name=request_result::$(echo $RESULT_REQUEST)
echo ::set-output name=concurrency_result::$(echo $RESULT_CONCURRENCY)
