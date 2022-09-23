import BuildHelper.Scala213
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object BenchmarkWorkFlow {
  private val s = "$"

  private def createBenchmarkResults(requestCounts: Int*): Seq[String] = {

    requestCounts.flatMap { count =>
      List(
        s"""RESULT_REQUEST=${s}(echo ${s}(grep -B 1 -A 17 "Concurrency: $count for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+")""",
        s"""RESULT_CONCURRENCY=${s}(echo ${s}(grep -B 1 -A 17 "Concurrency: $count for plaintext" result) | grep -oiE "concurrency: [0-9]+")""",
        s"""echo ::set-output name=request_result_$count::${s}(echo ${s}RESULT_REQUEST)""",
        s"""echo ::set-output name=concurrency_result_$count::${s}(echo ${s}RESULT_CONCURRENCY)""",
      )
    }

  }

  private def makeBenchmarkReport(requestCounts: Int*): String = {

    val header = "**\uD83D\uDE80 Performance Benchmark:**"

    requestCounts.foldLeft(header) { (report, count) =>
      s"""|$report
          |
          |${s}{{steps.result.outputs.concurrency_result_$count}}
          |${s}{{steps.result.outputs.request_result_$count}}""".stripMargin
    }

  }

  def apply(): Seq[WorkflowJob] = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "runBenchMarks",
      name = "Benchmarks",
      oses = List("centos"),
      cond = Some(
        "${{ github.event_name == 'pull_request'}}",
      ),
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
            "repository" -> "zio/FrameworkBenchmarks",
            "path"       -> "FrameworkBenchMarks",
          ),
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          id = Some("result"),
          commands = List(
            "cp ./zio-http/zio-http-example/src/main/scala/example/PlainTextBenchmarkServer.scala ./FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.scala",
            "cd ./FrameworkBenchMarks",
            """sed -i "s/---COMMIT_SHA---/${{github.event.pull_request.head.repo.owner.login}}\/zio-http.git#${{github.event.pull_request.head.sha}}/g" frameworks/Scala/zio-http/build.sbt""",
            "./tfb  --test zio-http | tee result",
          ) ++ createBenchmarkResults(256, 1024, 4096, 16384),
        ),
        WorkflowStep.Use(
          ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
          cond = Some(
            "${{github.event.pull_request.head.repo.full_name == 'zio/zio-http'}}",
          ),
          params = Map(
            "sha"  -> "${{github.event.pull_request.head.sha}}",
            "body" -> makeBenchmarkReport(256, 1024, 4096, 16384),
          ),
        ),
      ),
    ),
  ),

}
