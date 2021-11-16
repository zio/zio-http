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
              "repository" -> "https://github.com/amitksingh1490/FrameworkBenchmarks.git",
              "path"       -> "FrameworkBenchMarks",
            ),
          ),
          WorkflowStep.Run(
            commands = List(
              "pwd",
              "cp ./example/src/main/scala/example/HelloWorld.scala ./../FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.Scala",
              "cat ./../FrameworkBenchMarks/frameworks/Scala/zio-http/src/main/scala/Main.Scala",
            ),
          ),
        ),
      ),
    )
  }

}
