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
            commands = List(
              "cd ./FrameworkBenchMarks",
              "./tfb  --test zio-http > result.txt",
            ),
          ),
          WorkflowStep.Use(
            UseRef.Public("unsplash", "comment-on-pr", "v1.3.0"),
            Map(
              "msg" -> "${{grep -B 17 -A 1 \"Concurrency: 256 for plaintext\" result.txt}}",
            ),
          ),
        ),
      ),
    )
  }

}
