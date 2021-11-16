import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object BenchmarkWorkFlow {
  object benchmark {
    def apply(): Seq[WorkflowJob] = Seq(
      WorkflowJob(
        id = "runBenchMarks",
        name = "Benchmarks",
        env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
        steps = List(
          WorkflowStep.Checkout,
          WorkflowStep.Use(
            UseRef.Public("actions", "checkout", s"v2"),
            Map(
              "repository" -> "amitksingh1490/FrameworkBenchmarks",
              "path"       -> "FrameworkBenchMarks",
            ),
          ),
          WorkflowStep.Run(
            id = Some("result"),
            commands = List(
              "cd ./FrameworkBenchMarks",
              "sed 's/---COMMIT_SHA---/$GITHUB_SHA/g' frameworks/Scala/zio-http/build.sbt",
              "cat frameworks/Scala/zio-http/build.sbt",
              "echo ::set-output name=result::$(./tfb  --test zio-http | grep -B 1 -A 17 \"Concurrency: 256 for plaintext\" | grep Requests/sec)",
            ),
          ),
          WorkflowStep.Use(
            UseRef.Public("unsplash", "comment-on-pr", "v1.3.0"),
            Map(
              "msg" -> "${{steps.result.outputs.result}}",
            ),
          ),
        ),
      ),
    )
  }

}
