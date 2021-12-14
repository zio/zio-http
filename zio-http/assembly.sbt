assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
  case x                                                    =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
assembly / assemblyJarName       := "zhttp-1.0.0.0.jar"
