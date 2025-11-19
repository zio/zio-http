package zio.http

import zio._

import zio.stream._

package object datastar extends DatastarPackageBase {

  override private[datastar] val prefix: String     = "data"
  override private[datastar] val scriptName: String = "datastar.js"
  // All definitions must be inside DatastarPackageBase
}
