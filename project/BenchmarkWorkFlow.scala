import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}
import org.eclipse.jgit.lib.IndexDiff.WorkingTreeIteratorFactory

object BenchmarkWorkFlow {
  def apply(): Seq[WorkflowJob] = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "runBenchMarks",
      name = "Benchmarks",
      oses = List("centos"),
      cond = Some("${{ github.event_name == 'push'}}"),
      env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
      steps = List(
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
            """sed -i "s/---COMMIT_SHA---/${GITHUB_SHA}/g" frameworks/Scala/zio-http/build.sbt""",
            "./tfb  --test zio-http | tee result",
            """RESULT_REQUEST=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "requests/sec: [0-9]+.[0-9]+")""",
            """RESULT_CONCURRENCY=$(echo $(grep -B 1 -A 17 "Concurrency: 256 for plaintext" result) | grep -oiE "concurrency: [0-9]+")""",
            """echo ::set-output name=request_result::$(echo $RESULT_REQUEST)""",
            """echo ::set-output name=concurrency_result::$(echo $RESULT_CONCURRENCY)""",
          ),
        ),
        WorkflowStep.Use(
          UseRef.Public("unsplash", "comment-on-pr", "v1.3.0"),
          Map(
            "msg" -> "## \uD83D\uDE80\uD83D\uDE80\uD83D\uDE80 Benchmark Results \n **${{steps.result.outputs.concurrency_result}}** \n **${{steps.result.outputs.request_result}}**",
            "check_for_duplicate_msg" -> "false",
          ),
        ),
        WorkflowStep.Run(
          id = Some("clean_up"),
          name = Some("Clean up"),
          commands = List("sudo rm -rf *"),
        ),
      ),
    ),
  )
}
