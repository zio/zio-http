import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object BenchmarkWorkFlow {
  object benchmark {
    def apply(): Seq[WorkflowJob] = Seq(
      WorkflowJob(
        id = "runBenchMarks",
        name = "Benchmarks",
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
              "pwd",
              "ls ./",
              "cat ./FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.Scala",
              "cd ./FrameworkBenchMarks",
              "./tfb  --test zio-http",
            ),
          ),
        ),
      ),
    )
  }

}
