import BuildHelper.Scala213
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object ClientBenchmarkWorkFlow {
  def apply(): Seq[WorkflowJob] = Seq(
    WorkflowJob(
      runsOnExtraLabels = List("zio-http"),
      id = "runClientBenchmarks",
      name = "ClientBenchmarks",
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
            "repository" -> "sumawa/HttpClientBench",
            "path"       -> "HttpClientBench",
          ),
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          id = Some("clientResult"),
          commands = List(
            "cd ./HttpClientBench",
            """sed -i "s/---COMMIT_SHA---/${{github.event.pull_request.head.repo.owner.login}}\/zio-http.git#${{github.event.pull_request.head.sha}}/g" build.sbt""",
            "./run_client_benchmark.sh",
            "tail -n 100 ./log/server_out.log",
            """RESULT_CLIENTS="$(cat ./log/*.out)" """,
            """echo "$RESULT_CLIENTS" """,
            """echo ::set-output name=result_clients::$(echo "$RESULT_CLIENTS") """,
          ),
        ),
        WorkflowStep.Use(
          ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
          cond = Some(
            "${{github.event.pull_request.head.repo.full_name == 'dream11/zio-http'}}",
          ),
          params = Map(
            "sha"  -> "${{github.event.pull_request.head.sha}}",
            "body" ->
              """
                |**\uD83D\uDE80 Client Performance Benchmark:**
                || Client | Method | Number of Requests | RPS |
                ||--------|--------| -------------------| ----|
                |${{steps.clientResult.outputs.result_clients}}""".stripMargin,
          ),
        ),
      ),
    ),
  )
}
