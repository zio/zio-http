package zio.http.datastar

package object aliased extends DatastarPackageBase {
  override private[datastar] val prefix: String     = "data-star"
  override private[datastar] val scriptName: String = "datastar-aliased.js"
}
