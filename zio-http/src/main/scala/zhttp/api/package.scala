package zhttp

import zhttp.api.API.NotUnit
import zio.schema.Schema

import java.util.UUID
import scala.language.implicitConversions

package object api {

  // Paths
  val string: Path[String]   = Path.Match("string", Parser.stringParser, Schema[String])
  val int: Path[Int]         = Path.Match("int", Parser.intParser, Schema[Int])
  val boolean: Path[Boolean] = Path.Match("boolean", Parser.booleanParser, Schema[Boolean])
  val uuid: Path[UUID]       = Path.Match("uuid", Parser.uuidParser, Schema[UUID])

  // Query Params
  def string(name: String): Query[String]   = Query.SingleParam(name, Parser.stringParser, Schema[String])
  def int(name: String): Query[Int]         = Query.SingleParam(name, Parser.intParser, Schema[Int])
  def boolean(name: String): Query[Boolean] = Query.SingleParam(name, Parser.booleanParser, Schema[Boolean])

  implicit def stringToPath(string: String): Path[Unit] = Path.path(string)

  // API Ops

  implicit def apiToOps[Params, Input, Output: NotUnit, ZipOut](
    api: API[Params, Input, Output],
  )(implicit zipper: Zipper.WithOut[Params, Input, ZipOut]): APIOps[Params, Input, Output, ZipOut, api.Id] =
    new APIOps(api)

  implicit def apiToOpsUnit[Params, Input, ZipOut](
    api: API[Params, Input, Unit],
  )(implicit zipper: Zipper.WithOut[Params, Input, ZipOut]): APIOpsUnit[Params, Input, ZipOut, api.Id] =
    new APIOpsUnit(api)

}
