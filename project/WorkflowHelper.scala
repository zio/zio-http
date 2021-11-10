import sbtghactions.GenerativePlugin.autoImport.{WorkflowJob, WorkflowStep}

object WorkflowHelper {
  object Scoverage {
    // TODO move plugins to plugins.sbt after scoverage's support for Scala 3
    val scoveragePlugin        = """addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.9.1")"""
    val coverallsPlugin        = """addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.3.1")"""
    val coverageDirectivesBase = """(project in file("./zio-http"))"""

    val plugins = List(scoveragePlugin, coverageDirectivesBase)
    def apply(statementTotal: Double, branchTotal: Double): Seq[WorkflowJob] = {
      val coverageDirectivesSettings =
        s"settings(coverageEnabled:=true,coverageFailOnMinimum:=true,coverageMinimumStmtTotal:=${statementTotal},coverageMinimumBranchTotal:=${branchTotal})"
      Seq(
        WorkflowJob(
          id = "unsafeRunScoverage",
          name = "Unsafe Scoverage",
          steps = List(
            WorkflowStep.CheckoutFull,
            WorkflowStep.Run(
              commands = plugins.map(plugin => s"sed -i -e '$$a${plugin}' project/plugins.sbt"),
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
            WorkflowStep.Run(
              commands = List(s"sbt ++$${{ matrix.scala }} coverage 'project zhttp;test'"),
              id = Some("run_coverage"),
              name = Some("Run Coverage"),
            ),
            /*WorkflowStep.Run(
              commands = List("bash <(curl -s https://codecov.io/bash)"),
              id = Some("push_codecov"),
              name = Some("Push Codecov"),
            ),*/
            WorkflowStep.Sbt(
              commands = List("coverageReport coveralls"),
              id = Some("push_coveralls"),
              name = Some("Push Coveralls"),
              env = Map("COVERALLS_REPO_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}")
            )
          ),
        ),
      )
    }
  }
}
