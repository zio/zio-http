import BuildHelper.{JmhVersion, Scala213}
import sbt.nio.file.FileTreeView
import sbt.{**, Glob, PathFilter}
import sbtghactions.GenerativePlugin.autoImport.{UseRef, WorkflowJob, WorkflowStep}

object JmhBenchmarkWorkflow {

  val jmhPlugin                = s"""addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "$JmhVersion")"""
  val scalaSources: PathFilter = ** / "*.scala"
  val files = FileTreeView.default.list(Glob("./zio-http-benchmarks/src/main/**"), scalaSources)

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
    s"""sbt -no-colors -v "zioHttpBenchmarks/jmh:run -i 3 -wi 3 -f1 -t1 $str" | grep -e "thrpt" -e "avgt" >> ../${branch}_${list.head}.txt""".stripMargin,
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
          commands = List(
            s"""cat ${branch}_${l.head}.txt >> ${branch}_benchmarks.txt""".stripMargin,
          ),
          name = Some(s"Format_${branch}_${l.head}"),
      ),
    )
  })

  def checkout()= WorkflowStep.Use(
          UseRef.Public("actions", "checkout", "v2"),
          Map(
            "path" -> "zio-http",
          ),
        )

  def parse_results(branch: String) = WorkflowStep.Run(
        commands = List(s"""while IFS= read -r line; do
                           |   IFS=' ' read -ra PARSED_RESULT <<< "$$line"
                           |   echo $${PARSED_RESULT[1]} >> parsed_$branch.txt
                           |   B_VALUE=$$(echo $${PARSED_RESULT[1]}": "$${PARSED_RESULT[4]}" ops/sec")
                           |   echo $$B_VALUE >> $branch.txt
                           | done < ${branch}_benchmarks.txt""".stripMargin),
        id = Some(s"${branch}_Result"),
        name = Some(s"$branch Result"),
      )

  /**
   * Workflow Job to cache benchmark results
   */
  def cache(batchSize: Int) = Seq(
    WorkflowJob(
      id = "Jmh_cache",
      name = "Cache Jmh benchmarks",
//       cond = Some(
//        "${{ github.event_name == 'push' && github.ref == 'main'  }}",
//       ),
      needs = dependencies(batchSize),
      steps =  downloadArtifacts("Main", batchSize) ++
        Seq(
          checkout(),
          parse_results("Main"),
          WorkflowStep.Use(
          UseRef.Public("actions", "cache", "v4"),
          Map(
            "path" -> "Main.txt",
            "key" -> "criterion_benchmarks_${{ github.sha }}"
          ),
        ),
        ),
    ),
  )

  /**
   * Workflow Job to compare and publish benchmark results in the comment
   */
def jmh_compare(batchSize: Int) = Seq(
  WorkflowJob(
    id = "Comapre_jmh",
    name = "Compare Jmh",
    needs = dependencies(batchSize),
    steps = downloadArtifacts("Current", batchSize) ++
      Seq(
        parse_results("Current"),
        WorkflowStep.Use(
          ref = UseRef.Public("actions", "cache", "v4"),
          params = Map(
            "path" -> "benches/main_benchmarks.json",
            "key" -> "criterion_benchmarks_${{ github.event.pull_request.base.sha }}"
          )
        ),
        WorkflowStep.Run(
          commands = List(
            """bash zio-http/a.sh Main.txt Current.txt""",
            "cat benchmarks.md"
          ),
          id = Some("Create_md"),
          name = Some("Create md")
        ),
        WorkflowStep.Use(
          ref = UseRef.Public("peter-evans", "commit-comment", "v3"),
          params = Map(
            "sha" -> "${{github.sha}}",
            "body-path" -> "zio-http/benchmarks.md"
          )
        )
      )
  )
)


  /**
   * Workflow Job to run jmh benchmarks in batches parallelly
   */
  def run(batchSize: Int) = groupedBenchmarks(batchSize).map(l => {
    WorkflowJob(
      id = s"Jmh_${l.head}",
      name = s"Jmh ${l.head}",
      scalas = List(Scala213),
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
            "java-version" -> "11",
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
//          cond = Option("${{ github.event_name == 'push' && github.ref == 'main' }}"),
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
//          cond = Option("${{ github.event_name == 'push' && github.ref == 'main' }}"),
        ),
      ),
    )
  })

  def apply(batchSize: Int): Seq[WorkflowJob] = run(batchSize)  ++ cache(batchSize) ++ jmh_compare(batchSize)

}
