import sbtghactions.GenerativePlugin.autoImport.{WorkflowJob, WorkflowStep}

object WorkflowHelper {
  object Scoverage {
    val scoveragePlugin            = "addSbtPlugin(\"org.scoverage\" % \"sbt-scoverage\" % \"1.9.1\")"
    val coverageDirectivesBase     = "(project in file(\"./zio-http\"))"
    def apply(statementTotal: Double, branchTotal: Double): Seq[WorkflowJob] = {
      val coverageDirectivesSettings =
        s"settings(coverageEnabled:=true,coverageFailOnMinimum:=true,coverageMinimumStmtTotal:=${statementTotal},coverageMinimumBranchTotal:=${branchTotal})"
      Seq(
        WorkflowJob(
          id = "unsafeRunScoverage",
          name = "Unsafe Scoverage",
          steps = List(
            WorkflowStep.Run(
              commands = List(s"sed -i -e '$$a${scoveragePlugin}' /home/runner/work/zio-http/zio-http/project/plugins.sbt"),
              id = Some("add_plugin"),
              name = Some("Add Scoverage"),
            ),
            WorkflowStep.Run(
              commands = List(
                s"\nsed -i -e 's+${coverageDirectivesBase}+${coverageDirectivesBase}.${coverageDirectivesSettings}+g' /home/runner/work/zio-http/zio-http/build.sbt",
              ),
              id = Some("update_build_definition"),
              name = Some("Update Build Definition"),
            ),
            WorkflowStep.Sbt(
              commands = List(s"coverage test coverageReport"),
              id = Some("run_coverage"),
              name = Some("Run Coverage"),
            ),
          ),
        ),
      )
    }
  }
}
