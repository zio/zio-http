package zio.http

/**
 * Members of `URL` that exist only on the JVM: `toJavaURL`. On Scala.js this
 * trait has no members.
 */
trait URLPlatformSpecific {
  self: URL =>
}
