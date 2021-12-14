import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object BenchmarkWorkFlow {
  def apply(): Seq[WorkflowJob] = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "runBenchMarks",
      name = "Benchmarks",
      oses = List("centos"),
      cond = Some("${{ github.event_name == 'pull_request'}}"),
      steps = List(
        WorkflowStep.Run(
          id = Some("clean_up"),
          name = Some("Clean up"),
          commands = List("sudo rm -rf *"),
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", s"v2"),
          Map(
            "path" -> "zio-http",
          ),
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", s"v2"),
          Map(
            "repository" -> "dream11/FrameworkBenchmarks",
            "path"       -> "FrameworkBenchMarks",
            "ref"        -> "jar-poc",
          ),
        ),
        WorkflowStep.Run(
          id = Some("result"),
          commands = List(
            "cp ./zio-http/example/src/main/scala/example/PlainTextBenchmarkServer.scala ./FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.scala",
            "cd ./zio-http",
            "sbt zhttp/assembly",
            "ls ./zio-http/target/scala-2.13",
            "cp ./zio-http/target/scala-2.13/zhttp-1.0.0.0.jar ./../FrameworkBenchMarks/frameworks/Scala/zio-http/zhttp-1.0.0.0.jar",
            "cd ./../FrameworkBenchMarks",
            "echo Running Benchmarks",
            "./tfb  --test zio-http | tee result",
            """RESULT_REQUEST=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+")""",
            """RESULT_CONCURRENCY=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "concurrency: [0-9]+")""",
            """echo ::set-output name=request_result::$(echo $RESULT_REQUEST)""",
            """echo ::set-output name=concurrency_result::$(echo $RESULT_CONCURRENCY)""",
            "mkdir -p ./pr",
            "echo ${{ github.event.number }} > ./pr/NR",
            "echo ${{github.event.pull_request.head.sha}} > ./pr/SHA",
            "echo $RESULT_REQUEST > ./pr/Result",
            "echo $RESULT_CONCURRENCY ./pr/Concurrency",
          ),
        ),
      ),
    ),
  )
}
