package zio.http.datastar

import scala.language.implicitConversions

import zio.json._

import zio.schema._

import zio.http.template2._

final case class Signal[A](
  schema: Schema[A],
  name: SignalName,
) { self =>

  /** Create a nested signal by adding a property path */
  def nest(newPath: String): Signal[A] = copy(name = name.nest(newPath))

  /** Create a signal update assignment */
  def :=(value: A): SignalUpdate[A] = SignalUpdate(self, value)

  private var ref0: String = null

  /** Render the signal as a datastar expression reference */
  def ref: String = {
    if (ref0 == null && schema.isInstanceOf[Schema.Primitive[_]]) ref0 = name.ref
    else if (!schema.isInstanceOf[Schema.Primitive[_]])
      throw new RuntimeException(s"Signal.ref is only supported for primitive types, got: $schema")
    ref0
  }

  override def toString: String = ref
}

object Signal {
  def apply[A: Schema](name: SignalName): Signal[A]            =
    Signal(Schema[A], name)
  def apply[A: Schema](path: String): Signal[A]                =
    Signal(Schema[A], SignalName(path.split('.').toList.filter(!_.isBlank)))
  def apply[A: Schema](path: String, more: String*): Signal[A] =
    Signal(Schema[A], SignalName((path +: more).toList.flatMap(_.split('.')).filter(!_.isBlank)))

  def nested(path: String): NestedBuilder                =
    NestedBuilder(path.split('.').toList.filter(!_.isBlank))
  def nested(path: String, more: String*): NestedBuilder =
    NestedBuilder((path +: more).toList.flatMap(_.split('.')).filter(!_.isBlank))

  /** Helper for building nested signals with type inference */
  final case class NestedBuilder(path: List[String]) {
    def apply[A: Schema](name: String): Signal[A] = Signal(Schema[A], SignalName(path :+ name))
  }

  implicit def signalToJs[A](signal: Signal[A]): Js = js"$signal"
}

final case class SignalName(path: List[String]) { self =>
  assert(path.exists(!_.isBlank), "Signal name cannot be empty")
  assert(!path.exists(_.contains("__")), "Signal names cannot contain double underscores '__'")
  val name: String                                         = path.mkString(".")
  val ref: String                                          = s"$$$name"
  def toSignal[A: Schema]: Signal[A]                       = Signal(self)
  def toSignalUpdate[A: Schema](value: A): SignalUpdate[A] = SignalUpdate(toSignal[A], value)
  def nest(newPath: String): SignalName                    = copy(path = newPath :: path)
  def nest(newPath: String, more: String*): SignalName     =
    copy(path = ((newPath +: more) ++ path).toList.flatMap(_.split('.').toList))
  private[datastar] def isRoot: Boolean                    = path.length == 1
  override def toString: String                            = ref
}

object SignalName {
  def apply(path: String): SignalName                =
    SignalName(path.split('.').toList)
  def apply(path: String, more: String*): SignalName =
    SignalName((path +: more).toList.flatMap(_.split('.').toList))

  implicit def signalNameToJs(name: SignalName): Js = js"${name.ref}"
}

/**
 * Represents an assignment operation for updating a signal value. Can be used
 * with d-patch and other signal-updating attributes.
 */
final case class SignalUpdate[A](signal: Signal[A], value: A) {
  implicit val codec: JsonCodec[A] = zio.schema.codec.JsonCodec.jsonCodec(signal.schema)
  def toExpression: Js             = {
    val update = signal.schema match {
      case _: Schema.Primitive[_] => s"${signal.name.ref} = ${value.toJson.replace("\"", "'")}"
      case _                      =>
        val ast    = value.toJsonAST.getOrElse(throw new RuntimeException("Failed to convert value to JSON AST"))
        val nested = signal.name.path.foldRight(ast) { (key, acc) =>
          zio.json.ast.Json.Obj(key -> acc)
        }
        SignalUpdate.astToExpression(nested)
    }
    js"$update"
  }
}

object SignalUpdate {
  private def astToExpression(ast: zio.json.ast.Json): String   = ast match {
    case zio.json.ast.Json.Obj(fields) =>
      val fieldStrs = fields.map { case (k, v) => s"$k: ${astToExpression(v)}" }
      s"{${fieldStrs.mkString(", ")}}"
    case zio.json.ast.Json.Arr(items)  =>
      val itemStrs = items.map(astToExpression)
      s"[${itemStrs.mkString(", ")}]"
    case zio.json.ast.Json.Str(value)  => s"'$value'"
    case zio.json.ast.Json.Num(value)  => value.toString
    case zio.json.ast.Json.Bool(value) => value.toString
    case zio.json.ast.Json.Null        => "null"
  }
  implicit def signalUpdateToJs[A](update: SignalUpdate[A]): Js = update.toExpression
}
