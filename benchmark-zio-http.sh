ZIO_HTTP="zio/zio-http.git#$1"

if [ -z "$1" ]; then
    echo "You must supply the commit SHA to run benchmarks against."
    exit 1
fi

if [ ! -e "/var/run/docker.sock" ]
 then
    echo "'/var/run/docker.sock' does not exist.  Are you sure Docker is running?"
    exit 1
fi

if [ ! -d "../FrameworkBenchMarks" ]; then
    git clone https://github.com/zio/FrameworkBenchmarks.git ../FrameworkBenchMarks
fi

rm ../FrameworkBenchMarks/frameworks/Scala/zio-http/build.sbt
rm ../FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.scala
cp ../FrameworkBenchMarks/frameworks/Scala/zio-http/base.build.sbt ../FrameworkBenchMarks/frameworks/Scala/zio-http/build.sbt
cp ./zio-http-example/src/main/scala/example/PlainTextBenchmarkServer.scala ../FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.scala
cd ../FrameworkBenchMarks
sed -i '' "s|---COMMIT_SHA---|${ZIO_HTTP}|g" frameworks/Scala/zio-http/build.sbt
./tfb --test zio-http | tee result
RESULT_REQUEST=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+")
RESULT_CONCURRENCY=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "concurrency: [0-9]+")
echo ::set-output name=request_result::$(echo $RESULT_REQUEST)
echo ::set-output name=concurrency_result::$(echo $RESULT_CONCURRENCY)
