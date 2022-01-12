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
            """NUM_GET_REQUESTS_CLIENT=$(grep -Eo -i "number of GET requests: *[0-9]+" client_benchmark.log)""",
            """NUM_POST_REQUESTS_CLIENT=$(grep -Eo -i "number of POST requests: *[0-9]+" client_benchmark.log)""",
            """GET_REQUESTS_PER_SECONDS_CLIENT=$(grep -Eoi "GET requests/sec: *[0-9]+" client_benchmark.log)""",
            """POST_REQUESTS_PER_SECONDS_CLIENT=$(grep -Eoi "POST requests/sec: *[0-9]+" client_benchmark.log)""",
            """echo ::set-output name=request_result::$(echo $RESULT_REQUEST)""",
            """echo ::set-output name=concurrency_result::$(echo $RESULT_CONCURRENCY)""",
            """echo ::set-output name=get_requests_per_seconds_client::$(echo $GET_REQUESTS_PER_SECONDS_CLIENT)""",
            """echo ::set-output name=num_get_requests_client::$(echo $NUM_GET_REQUESTS_CLIENT)""",
            """echo ::set-output name=post_requests_per_seconds_client::$(echo $POST_REQUESTS_PER_SECONDS_CLIENT)""",
            """echo ::set-output name=num_post_requests_client::$(echo $NUM_POST_REQUESTS_CLIENT)""",
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
                |${{steps.result.outputs.num_get_requests_client}}
                |${{steps.result.outputs.get_requests_per_seconds_client}}
                |${{steps.result.outputs.num_post_requests_client}}
                |${{steps.result.outputs.post_requests_per_seconds_client}}
                |""".stripMargin,
          ),
        ),
      ),
    ),
  )
}
