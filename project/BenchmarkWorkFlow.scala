import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object BenchmarkWorkFlow {
  def apply(): Seq[WorkflowJob] = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "runBenchMarks",
      name = "Benchmarks",
      oses = List("centos"),
      cond = Some("${{ github.event_name == 'pull_request'}}"),
      env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
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
          ),
        ),
        WorkflowStep.Run(
          id = Some("result"),
          commands = List(
            "cd ./FrameworkBenchMarks",
            "echo ${{github.event.pull_request.head.sha}}",
            """sed -i "s/---COMMIT_SHA---/${{github.event.pull_request.head.sha}}/g" frameworks/Scala/zio-http/build.sbt""",
            "./tfb  --test zio-http | tee result",
            """RESULT_REQUEST=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+")""",
            """RESULT_CONCURRENCY=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "concurrency: [0-9]+")""",
            """echo ::set-output name=request_result::$(echo $RESULT_REQUEST)""",
            """echo ::set-output name=concurrency_result::$(echo $RESULT_CONCURRENCY)""",
          ),
        ),
        WorkflowStep.Use(
          ref = UseRef.Public("unsplash", "comment-on-pr", "v1.3.0"),
          params = Map(
            "msg" -> "## \uD83D\uDE80\uD83D\uDE80\uD83D\uDE80 Benchmark Results \n **${{steps.result.outputs.concurrency_result}}** \n **${{steps.result.outputs.request_result}}**",
            "check_for_duplicate_msg" -> "false",
          ),
        ),
      ),
    ),
  )
}
