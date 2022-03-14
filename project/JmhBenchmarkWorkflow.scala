import BuildHelper.{JmhVersion, Scala213}
import sbt.nio.file.FileTreeView
import sbt.{**, Glob, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhBenchmarkWorkflow {

  val jmhPlugin = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "$JmhVersion")"""
  val scalaSources: PathFilter = ** / "*.scala"
  val files = FileTreeView.default.list(Glob("./zio-http-benchmarks/src/main/scala/zhttp.benchmarks/**"), scalaSources)

  /**
  Get zhttpBenchmark file names
   */
  def getFilenames = files.map(file => {
    val path = file._1.toString
    path.replaceAll("^.*[\\/\\\\]", "").replaceAll(".scala", "")
  }).sorted

  /**
  Run jmh benchmarks and store result
   */
  def runSBT(list: Seq[String], branch: String = "Current") = list.map(str =>
    s"""sbt -no-colors -v "zhttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" | grep "thrpt" >> ../${branch}_${list.head}.txt""".stripMargin)

  /**
  Group benchmarks into batches
   */
  def groupedBenchmarks(batchSize: Int) = getFilenames.grouped(batchSize).toList

  /**
  Get dependent jobs for publishing the result
   */
  def dependencies(batchSize: Int) = groupedBenchmarks(batchSize).flatMap((l: Seq[String]) => List(s"Jmh_Current_${l.head}",s"Jmh_Main_${l.head}"))

  /**
  Download Artifacts and parse result
   */
  def downloadArtifacts(branch: String, batchSize: Int) = groupedBenchmarks(batchSize).flatMap(l => {
    Seq(
      WorkflowStep.Use(
        ref = UseRef.Public("actions", "download-artifact", "v3"),
        Map(
          "name" -> s"Jmh_${branch}_${l.head}"
        )
      ),
      WorkflowStep.Run(
        commands = List(
          s"""while IFS= read -r line; do
             |   IFS=' ' read -ra PARSED_RESULT <<< "$$line"
             |   B_VALUE=$$(echo $${PARSED_RESULT[1]}": "$${PARSED_RESULT[4]}" ops/sec")
             |   echo $$B_VALUE >> $branch.txt
             | done < ${branch}_${l.head}.txt""".stripMargin),
        id = Some(s"Result_${branch}_${l.head}"),
        name = Some(s"Result $branch ${l.head}")
      )
    )
  })

  /**
  Format result and set output
   */
  def setOutput(branch: String) = WorkflowStep.Run(
    commands = List(
      s"""body=$$(cat $branch.txt)
         | body="$${body//'%'/'%25'}"
         | body="$${body//$$'\\n'/'%0A'}"
         | body="$${body//$$'\\r'/'%0D'}"
         | echo $$body
         | echo ::set-output name=body::$$(echo $$body)
         | """.stripMargin
    ),
    id = Some(s"Set_output_$branch"),
    name = Some(s"Set Output $branch")
  )

  /**
  Workflow Job to publish benchmark results in the comment
   */
  def publish(batchSize: Int) = Seq(WorkflowJob(
    id = "Jmh_publish",
    name = "Jmh Publish",
    scalas = List(Scala213),
    cond = Some(
      "${{ github.event_name == 'pull_request' && github.event.label.name == 'jmhBenchmark' }}"
    ),
    needs =  dependencies(batchSize),
    steps = downloadArtifacts("Current", batchSize) ++ downloadArtifacts("Main", batchSize) ++
      Seq(setOutput("Current"), setOutput("Main"), WorkflowStep.Use(
        ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
        params = Map(
          "sha" -> "${{github.sha}}",
          "body" ->
            """
              |**\uD83D\uDE80 Jmh Benchmark:**
              |
              |- **Current Branch**:
              | ${{steps.set_output_Current.outputs.body}}
              |
              |- **Main Branch**:
              | ${{steps.set_output_Main.outputs.body}}
              | """.stripMargin
        )
      )
      )
  ))

  /**
  Workflow Job to run jmh benchmarks in batches parallelly
   */
  def run(batchSize: Int, branch: String) = groupedBenchmarks(batchSize).map(l => {
    val checkout = if(branch == "Current") "" else "main"
    WorkflowJob(
      id = s"Jmh_${branch}_${l.head}",
      name = s"Jmh ${branch} ${l.head}",
      scalas = List(Scala213),
      cond = Some(
       "${{ github.event_name == 'pull_request' && github.event.label.name == 'jmhBenchmark' }}"
      ),
      steps = List(
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", "v2"),
          Map(
            "path" -> "zio-http",
            "ref" -> checkout
          )
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "setup-java", "v2"),
          Map(
            "distribution" -> "temurin",
            "java-version" -> "8"
          )
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          commands = List("cd zio-http", s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt", s"cat > ${branch}_${l.head}.txt") ++ runSBT(l, branch),
          id = Some("Benchmark"),
          name = Some("Benchmark")
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "upload-artifact", "v3"),
          Map(
            "name" -> s"Jmh_${branch}_${l.head}",
            "path" -> s"${branch}_${l.head}.txt"
          )
        )
      )
    )
  }
  )

  def apply(batchSize: Int): Seq[WorkflowJob] = run(batchSize, "Current") ++ run(batchSize, "Main") ++ publish(batchSize)

}