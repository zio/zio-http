import BuildHelper.{Scala213, ScoverageVersion}
import sbtghactions.GenerativePlugin.autoImport.{WorkflowJob, WorkflowStep}

object ScoverageWorkFlow {
  // TODO move plugins to plugins.sbt after scoverage's support for Scala 3
  val scoveragePlugin        = s"""addSbtPlugin("org.scoverage" % "sbt-scoverage" % "${ScoverageVersion}")"""
  val coverageDirectivesBase = """(project in file("./zio-http"))"""

  def apply(statementTotal: Double, branchTotal: Double): Seq[WorkflowJob] = {
    val coverageDirectivesSettings =
      s"settings(coverageEnabled:=true,coverageMinimumStmtTotal:=${statementTotal},coverageMinimumBranchTotal:=${branchTotal})"
    Seq(
      WorkflowJob(
        id = "unsafeRunScoverage",
        name = "Unsafe Scoverage",
        scalas = List(Scala213),
        steps = List(
          WorkflowStep.CheckoutFull,
          WorkflowStep.Run(
            commands = List(s"sed -i -e '$$a${scoveragePlugin}' project/plugins.sbt"),
            id = Some("add_plugin"),
            name = Some("Add Scoverage"),
          ),
          WorkflowStep.Run(
            commands = List(
              s"\nsed -i -e 's+${coverageDirectivesBase}+${coverageDirectivesBase}.${coverageDirectivesSettings}+g' build.sbt",
            ),
            id = Some("update_build_definition"),
            name = Some("Update Build Definition"),
          ),
          WorkflowStep.Sbt(
            commands = List(s"coverage; project zioHttp; test; coverageReport"),
            id = Some("run_coverage"),
            name = Some("Run Coverage"),
          ),
          WorkflowStep.Run(
            commands = List("bash <(curl -s https://codecov.io/bash)"),
            id = Some("push_codecov"),
            name = Some("Push Codecov"),
          ),
        ),
      ),
    )
  }
}
