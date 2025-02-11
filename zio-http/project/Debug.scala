/**
 * Contains a list of configurations to play around with while debugging the
 * internals of ZIO Http.
 */
object Debug {

  /**
   * Sets the global log level for ZIO Http. NOTE: A clean build will be
   * required once this is changed. After which you can run the compiler in
   * watch mode.
   *
   * Possible Values: DEBUG, ERROR, INFO, TRACE, WARN
   */
  val ZIOHttpLogLevel = "INFO"

  /**
   * Sets the main application to execute in the example project.
   */
  // val Main = "zio.http.endpoint.EndpointExamples"
  val Main = "zio.http.endpoint.BasicAuthAPIExample"
  // val Main = "example.BasicAuth"
}
