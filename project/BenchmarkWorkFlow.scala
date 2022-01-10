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
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
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
          ),
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          id = Some("result"),
          commands = List(
            "cp ./zio-http/example/src/main/scala/example/PlainTextBenchmarkServer.scala ./FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.scala",
            "cd ./FrameworkBenchMarks",
            "echo ${{github.event.pull_request.head.sha}}",
            """sed -i "s/---COMMIT_SHA---/${{github.event.pull_request.head.sha}}/g" frameworks/Scala/zio-http/build.sbt""",
            "./tfb  --test zio-http | tee result",
            """RESULT_REQUEST=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+")""",
            """RESULT_CONCURRENCY=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "concurrency: [0-9]+")""",
            "cd ../zio-http",
            "./example/src/main/resources/benchmark_runner.sh",
            """NUM_REQUESTS_CLIENT=$(grep -Eo -i "number of requests: *[0-9]+" client_benchmark.log)""",
            """REQUESTS_PER_SECONDS_CLIENT=$(grep -Eoi "requests/sec: *[0-9]+" client_benchmark.log)""",
            """echo ::set-output name=request_result::$(echo $RESULT_REQUEST)""",
            """echo ::set-output name=concurrency_result::$(echo $RESULT_CONCURRENCY)""",
            """echo ::set-output name=requests_per_seconds_client::$(echo $REQUESTS_PER_SECONDS_CLIENT)""",
            """echo ::set-output name=num_requests_client::$(echo $NUM_REQUESTS_CLIENT)""",
          ),
        ),
        WorkflowStep.Use(
          ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
          params = Map(
            "sha"  -> "${{github.event.pull_request.head.sha}}",
            "body" ->
              """
                |**\uD83D\uDE80 Performance Benchmark:**
                |
                |## Server Benchmark
                |${{steps.result.outputs.concurrency_result}}
                |${{steps.result.outputs.request_result}}
                |
                |## Client Benchmark
                |${{steps.result.outputs.num_requests_client}}
                |${{steps.result.outputs.requests_per_seconds_client}}
                |""".stripMargin,
          ),
        ),
      ),
    ),
  )
}
