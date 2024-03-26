import BuildHelper.{JmhVersion, Scala213}
import sbt.nio.file.FileTreeView
import sbt.{**, Glob, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhBenchmarkWorkflow {

  val jmhPlugin                = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "$JmhVersion")"""
  val scalaSources: PathFilter = ** / "*.scala"
  val files = FileTreeView.default.list(Glob("./zio-http-benchmarks/src/main/scala/zio.benchmarks/**"), scalaSources)

  /**
   * Get zioHttpBenchmark file names
   */
  def getFilenames = files
    .map(file => {
      val path = file._1.toString
      path.replaceAll("^.*[\\/\\\\]", "").replaceAll(".scala", "")
    })
    .sorted

  /**
   * Run jmh benchmarks and store result
   */
  def runSBT(list: Seq[String], branch: String) = list.map(str =>
    s"""sbt -no-colors -v "zioHttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" | grep "thrpt" >> ../${branch}_${list.head}.txt""".stripMargin,
  )

  /**
   * Group benchmarks into batches
   */
  def groupedBenchmarks(batchSize: Int) = getFilenames.grouped(batchSize).toList

  /**
   * Get dependent jobs for publishing the result
   */
  def dependencies(batchSize: Int) = groupedBenchmarks(batchSize).flatMap((l: Seq[String]) => List(s"Jmh_${l.head}"))

  /**
   * Download Artifacts and parse result
   */
  def downloadArtifacts(branch: String, batchSize: Int) = groupedBenchmarks(batchSize).flatMap(l => {
    Seq(
      WorkflowStep.Use(
        ref = UseRef.Public("actions", "download-artifact", "v3"),
        Map(
          "name" -> s"Jmh_${branch}_${l.head}",
        ),
      ),
      WorkflowStep.Run(
        commands = List(s"""while IFS= read -r line; do
                           |   IFS=' ' read -ra PARSED_RESULT <<< "$$line"
                           |   echo $${PARSED_RESULT[1]} >> parsed_$branch.txt
                           |   B_VALUE=$$(echo $${PARSED_RESULT[1]}": "$${PARSED_RESULT[4]}" ops/sec")
                           |   echo $$B_VALUE >> $branch.txt
                           | done < ${branch}_${l.head}.txt""".stripMargin),
        id = Some(s"Result_${branch}_${l.head}"),
        name = Some(s"Result $branch ${l.head}"),
      ),
    )
  })

  /**
   * Format result and set output
   */
  def formatOutput() = WorkflowStep.Run(
    commands = List(
      s"""cat parsed_Current.txt parsed_Main.txt | sort -u > c.txt
         |          while IFS= read -r line; do
         |          if grep -q "$$line" Current.txt
         |          then
         |          grep "$$line" Current.txt | sed 's/^.*: //' >> finalCurrent.txt;
         |          else
         |          echo "" >> finalCurrent.txt;
         |          fi
         |            if grep -q "$$line" Main.txt
         |          then
         |          grep "$$line" Main.txt | sed 's/^.*: //' >> finalMain.txt;
         |          else
         |          echo "" >> finalMain.txt;
         |          fi
         |           done < c.txt
         |paste -d '|' c.txt finalCurrent.txt finalMain.txt > FinalOutput.txt
         | sed -i -e 's/^/|/' FinalOutput.txt
         | sed -i -e 's/$$/|/' FinalOutput.txt
         | body=$$(cat FinalOutput.txt)
         | body="$${body//'%'/'%25'}"
         | body="$${body//$$'\\n'/'%0A'}"
         | body="$${body//$$'\\r'/'%0D'}"
         | echo $$body
         | echo ::set-output name=body::$$(echo $$body)
         | """.stripMargin,
    ),
    id = Some(s"fomat_output"),
    name = Some(s"Format Output"),
  )

  /**
   * Workflow Job to publish benchmark results in the comment
   */
  def publish(batchSize: Int) = Seq(
    WorkflowJob(
      id = "Jmh_publish",
      name = "Jmh Publish",
      scalas = List(Scala213),
      cond = Some(
        "${{ github.event.label.name == 'run jmh' && github.event_name == 'pull_request' }}",
      ),
      needs = dependencies(batchSize),
      steps = downloadArtifacts("Current", batchSize) ++ downloadArtifacts("Main", batchSize) ++
        Seq(
          formatOutput(),
          WorkflowStep.Use(
            ref = UseRef.Public("peter-evans", "commit-comment", "v1"),
            params = Map(
              "sha"  -> "${{github.sha}}",
              "body" ->
                """
                  |**\uD83D\uDE80 Jmh Benchmark:**
                  |
                  ||Name |Current| Main|
                  ||-----|----| ----|
                  | ${{steps.fomat_output.outputs.body}}
                  | """.stripMargin,
            ),
          ),
        ),
    ),
  )

  /**
   * Workflow Job to run jmh benchmarks in batches parallelly
   */
  def run(batchSize: Int) = groupedBenchmarks(batchSize).map(l => {
    WorkflowJob(
      id = s"Jmh_${l.head}",
      name = s"Jmh ${l.head}",
      scalas = List(Scala213),
      cond = Some(
        "${{ github.event.label.name == 'run jmh' && github.event_name == 'pull_request' }}",
      ),
      steps = List(
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", "v2"),
          Map(
            "path" -> "zio-http",
          ),
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "setup-java", "v2"),
          Map(
            "distribution" -> "temurin",
            "java-version" -> "8",
          ),
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          commands = List(
            "cd zio-http",
            s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt",
            s"cat > Current_${l.head}.txt",
          ) ++ runSBT(l, "Current"),
          id = Some("Benchmark_Current"),
          name = Some("Benchmark_Current"),
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "upload-artifact", "v3"),
          Map(
            "name" -> s"Jmh_Current_${l.head}",
            "path" -> s"Current_${l.head}.txt",
          ),
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "checkout", "v2"),
          Map(
            "path" -> "zio-http",
            "ref"  -> "main",
          ),
        ),
        WorkflowStep.Run(
          env = Map("GITHUB_TOKEN" -> "${{secrets.ACTIONS_PAT}}"),
          commands = List(
            "cd zio-http",
            s"sed -i -e '$$a${jmhPlugin}' project/plugins.sbt",
            s"cat > Main_${l.head}.txt",
          ) ++ runSBT(l, "Main"),
          id = Some("Benchmark_Main"),
          name = Some("Benchmark_Main"),
        ),
        WorkflowStep.Use(
          UseRef.Public("actions", "upload-artifact", "v3"),
          Map(
            "name" -> s"Jmh_Main_${l.head}",
            "path" -> s"Main_${l.head}.txt",
          ),
        ),
      ),
    )
  })

  def apply(batchSize: Int): Seq[WorkflowJob] = run(batchSize) ++ publish(batchSize)

}
