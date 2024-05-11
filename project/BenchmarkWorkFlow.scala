import BuildHelper.Scala213
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object BenchmarkWorkFlow {
  def apply(): Seq[WorkflowJob] = Seq(
    makeBenchmarkPass(
      "runBenchmarks-simple",
      "Performance Benchmarks (PlainTextBenchmarkServer)",
      300000,
      "PlainTextBenchmarkServer",
    ),
    makeBenchmarkPass(
      "runBenchmarks-effectful",
      "Performance Benchmarks (SimpleEffectBenchmarkServer)",
      300000,
      "SimpleEffectBenchmarkServer",
    ),
  )

  private def makeBenchmarkPass(id: String, name: String, performanceFloor: Int, server: String) =
    WorkflowJob(
//      runsOnExtraLabels = List("zio-http"),
      id = id,
      name = name,
      oses = List("ubuntu-latest"),
//      cond = Some(
//        "${{ github.event_name == 'pull_request'}}",
//      ),
      scalas = List(Scala213),
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
            "repository" -> "khajavi/FrameworkBenchmarks",
            "path"       -> "FrameworkBenchMarks",
          ),
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          id = Some("result"),
          commands = List(
            "mkdir -p ./FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala",
            s"cp ./zio-http/zio-http-example/src/main/scala/example/${server}.scala ./FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.scala",
            "cd ./FrameworkBenchMarks",
            "cd ./frameworks/Scala/zio-http",
            "git init .",
            "git config user.email 'benchamrk@example.com'",
            "git config user.name 'ZIO Benchmark'",
            "git add build.sbt",
            "git commit -m 'initial commit'",
            "git tag v1.0.0",
            "git clone https://github.com/${{github.event.pull_request.head.repo.owner.login}}/zio-http.git",
            "cd zio-http",
            "git checkout ${{github.event.pull_request.head.sha}}",
            "cd ../../../..",
            "./tfb  --test zio-http | tee result",
            """RESULT_REQUEST=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+" | grep -oiE "[0-9]+" | head -1)""",
            """RESULT_CONCURRENCY=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "concurrency: [0-9]+" | grep -oiE "[0-9]+")""",
            """echo "request_per_second=$RESULT_REQUEST" >> $GITHUB_OUTPUT""",
            """echo "concurrency=$RESULT_CONCURRENCY" >> $GITHUB_OUTPUT""",
          ),
        ),
        WorkflowStep.Use(
          ref = UseRef.Public("peter-evans", "commit-comment", "v2"),
          cond = Some(
            "${{github.event.pull_request.head.repo.full_name == 'zio/zio-http'}}",
          ),
          params = Map(
            "sha"  -> "${{github.event.pull_request.head.sha}}",
            "body" ->
              (s"""
                 |**\uD83D\uDE80 :** $name""" + """
                                                              |
                                                              | concurrency: ${{steps.result.outputs.concurrency}}
                                                              | requests/sec:  ${{steps.result.outputs.request_per_second}}
                """).stripMargin,
          ),
        ),
        WorkflowStep.Run(
          name = Some("Performance Report"),
          id = Some("perf-report"),
          env = Map(
            "REQUESTS_PER_SECOND" -> "${{steps.result.outputs.request_per_second}}",
            "CONCURRENCY"         -> "${{steps.result.outputs.concurrency}}",
            "PERFORMANCE_FLOOR"   -> s"${performanceFloor}",
          ),
          commands = List(
            s"""echo "** 🚀 ${name} Report 🚀 **" """,
            """
              |echo "$REQUESTS_PER_SECOND requests/sec for $CONCURRENCY concurrent requests"

              |if (( REQUESTS_PER_SECOND > PERFORMANCE_FLOOR )); then
              |  echo "Woohoo! Performance is good! $REQUESTS_PER_SECOND requests/sec exceeds the performance floor of $PERFORMANCE_FLOOR requests/sec."
              |else 
              |  echo "Performance benchmark failed with $REQUESTS_PER_SECOND req/sec! Performance must exceed $PERFORMANCE_FLOOR req/sec."
              |   exit 1
              |fi""".stripMargin,
          ),
        ),
      ),
    )
}
