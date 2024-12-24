sys.props.get("plugin.version") match {
  case Some(ver) => addSbtPlugin("dev.zio" % "zio-http-sbt-codegen" % ver)
  case None      => sys.error("""|The system property 'plugin.version' is not defined.
                                 |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}