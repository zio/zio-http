package zio.http.datastar.signal

import scala.language.implicitConversions

import zio.json._

import zio.schema._

import zio.http.datastar.Attributes.CaseModifier
import zio.http.template2._

final case class Signal[A](
  schema: Schema[A],
  name: SignalName,
) { self =>

  def caseModifier(newCaseModifier: CaseModifier): Signal[A] =
    copy(name = name.caseModifier(newCaseModifier))

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

  def update(in: A): SignalUpdate[A] = SignalUpdate(self, in)
}

object Signal {
  def apply[A: Schema](name: SignalName): Signal[A]            =
    Signal(Schema[A], name)
  def apply[A: Schema](path: String): Signal[A]                =
    Signal(Schema[A], SignalName(path.split('.').toList.filter(!_.isBlank), CaseModifier.Camel))
  def apply[A: Schema](path: String, more: String*): Signal[A] =
    Signal(Schema[A], SignalName((path +: more).toList.flatMap(_.split('.')).filter(!_.isBlank), CaseModifier.Camel))

  def nested(path: String): NestedBuilder                =
    NestedBuilder(path.split('.').toList.filter(!_.isBlank))
  def nested(path: String, more: String*): NestedBuilder =
    NestedBuilder((path +: more).toList.flatMap(_.split('.')).filter(!_.isBlank))

  /** Helper for building nested signals with type inference */
  final case class NestedBuilder(path: List[String], caseModifier: CaseModifier = CaseModifier.Camel) {
    def nest(newPath: String): NestedBuilder                    = copy(path = newPath :: path)
    def nest(newPath: String, more: String*): NestedBuilder     =
      copy(path = ((newPath +: more) ++ path).toList.flatMap(_.split('.').toList))
    def caseModifier(caseModifier: CaseModifier): NestedBuilder =
      NestedBuilder(path, caseModifier)
    def apply[A: Schema](name: String): Signal[A] = Signal(Schema[A], SignalName(path :+ name, caseModifier))
    def apply[A: Schema](caseModifier: CaseModifier)(name: String): Signal[A] =
      Signal(Schema[A], SignalName(path :+ name, caseModifier))
  }

  implicit def signalToJs[A](signal: Signal[A]): Js = js"$signal"
}

final case class SignalName(path: List[String], caseModifier: CaseModifier) { self =>
  assert(path.exists(!_.isBlank), "Signal name cannot be empty")
  assert(!path.exists(_.contains("__")), "Signal names cannot contain double underscores '__'")
  def caseModifier(newCaseModifier: CaseModifier): SignalName =
    copy(caseModifier = newCaseModifier)
  val name: String                                            = path.map(CaseModifier.Kebab.modify).mkString(".")
  val ref: String                                             = s"$$${path.map(caseModifier.modify).mkString(".")}"
  def toSignal[A: Schema]: Signal[A]                          = Signal(self)
  def toSignalUpdate[A: Schema](value: A): SignalUpdate[A]    = SignalUpdate(toSignal[A], value)
  def nest(newPath: String): SignalName                       = copy(path = newPath :: path)
  def nest(newPath: String, more: String*): SignalName        =
    copy(path = ((newPath +: more) ++ path).toList.flatMap(_.split('.').toList))
  private[datastar] def isRoot: Boolean                       = path.length == 1
  override def toString: String                               = ref
}

object SignalName {
  def apply(path: String, more: String*): SignalName                             =
    SignalName((path +: more).toList.flatMap(_.split('.').toList), CaseModifier.Camel)
  def apply(caseModifier: CaseModifier)(path: String, more: String*): SignalName =
    SignalName((path +: more).toList.flatMap(_.split('.').toList), caseModifier)

  implicit def signalNameToJs(name: SignalName): Js = js"${name.ref}"
}

/**
 * Represents an assignment operation for updating a signal value. Can be used
 * with d-patch and other signal-updating attributes.
 */
final case class SignalUpdate[A](signal: Signal[A], value: A) {
  private implicit val codec: JsonCodec[A] = zio.schema.codec.JsonCodec.jsonCodec(signal.schema)
  def toExpression: Js                     = {
    val update = signal.schema match {
      case _: Schema.Primitive[_] => value.toJson.replace("\"", "'")
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
