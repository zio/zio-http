package zio.http

import zio._

import zio.stream._

package object datastar extends DatastarPackageBase {

  override private[datastar] val prefix: String = "data"

  // All definitions must be inside DatastarPackageBase
}
