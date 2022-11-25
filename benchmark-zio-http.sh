COMMIT_SHA=$(git rev-parse --short HEAD)
ZIO_HTTP="zio/zio-http.git#$COMMIT_SHA"

if [ -z "$1" ]; then
    SERVER=PlainTextBenchmarkServer
else
    SERVER=$1
fi

if [ ! -e "/var/run/docker.sock" ]; then
    echo "'/var/run/docker.sock' does not exist.  Are you sure Docker is running?"
    exit 1
fi

if [ ! -d "../FrameworkBenchMarks" ]; then
    git clone https://github.com/zio/FrameworkBenchmarks.git ../FrameworkBenchMarks
    git checkout master
fi

mkdir -p ../FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala
rm ../FrameworkBenchMarks/frameworks/Scala/zio-http/build.sbt
rm ../FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.scala
cp ../FrameworkBenchMarks/frameworks/Scala/zio-http/base.build.sbt ../FrameworkBenchMarks/frameworks/Scala/zio-http/build.sbt
cp ./zio-http-example/src/main/scala/example/$SERVER.scala ../FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.scala
cd ../FrameworkBenchMarks
sed -i '' "s|---COMMIT_SHA---|${ZIO_HTTP}|g" frameworks/Scala/zio-http/build.sbt
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
