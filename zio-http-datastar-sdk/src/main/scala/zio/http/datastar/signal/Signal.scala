package zio.http.datastar.signal

import scala.language.implicitConversions

import zio.json._

import zio.schema._

import zio.http.datastar.Attributes.CaseModifier
import zio.http.template2._

/**
 * A Datastar signal of type `A`: a name and the `Schema` of its values. `ref`
 * gives the reference that expressions use, for example `\$count` or
 * `\$user.name`, and `:=` or `update` build a `SignalUpdate` that assigns a
 * value.
 */
final case class Signal[A](
  schema: Schema[A],
  name: SignalName,
) { self =>

  /**
   * Returns a copy whose reference writes each path segment with
   * `newCaseModifier`.
   */
  def caseModifier(newCaseModifier: CaseModifier): Signal[A] =
    copy(name = name.caseModifier(newCaseModifier))

  /**
   * Returns a signal with `newPath` added in front of the current path, so
   * `Signal[Int]("a").nest("b")` has the reference `\$b.a`.
   */
  def nest(newPath: String): Signal[A] = copy(name = name.nest(newPath))

  /** Create a signal update assignment */
  def :=(value: A): SignalUpdate[A] = SignalUpdate(self, value)

  /**
   * The reference of the signal as expressions write it: `\$` followed by the
   * path segments, written with the case modifier and joined with `.`, for
   * example `\$my.nested.count`. Throws a `java.lang.RuntimeException` whose
   * message starts with
   * `Signal.ref is only supported for primitive types, got:` when the schema is
   * not a primitive.
   */
  lazy val ref: String = {
    if (!schema.isInstanceOf[Schema.Primitive[_]])
      throw new RuntimeException(s"Signal.ref is only supported for primitive types, got: $schema")
    name.ref
  }

  override def toString: String = ref

  /**
   * Returns a `SignalUpdate` that assigns `in` to this signal. It is the same
   * as `:=`.
   */
  def update(in: A): SignalUpdate[A] = SignalUpdate(self, in)
}

/** Constructors for `Signal`. */
object Signal {

  /** Returns a signal of type `A` with the given name. */
  def apply[A: Schema](name: SignalName): Signal[A] =
    Signal(Schema[A], name)

  /**
   * Returns a signal of type `A` whose path is `path` split at the dots, with
   * blank parts dropped. Path segments are written in camel case. Throws a
   * `java.lang.AssertionError` when no non-blank part is left.
   */
  def apply[A: Schema](path: String): Signal[A] =
    Signal(Schema[A], SignalName(path.split('.').toList.filter(!_.isBlank), CaseModifier.Camel))

  /**
   * Returns a signal of type `A` whose path is `path` followed by `more`, with
   * every argument split at the dots and blank parts dropped. Path segments are
   * written in camel case. Throws a `java.lang.AssertionError` when no
   * non-blank part is left.
   */
  def apply[A: Schema](path: String, more: String*): Signal[A] =
    Signal(Schema[A], SignalName((path +: more).toList.flatMap(_.split('.')).filter(!_.isBlank), CaseModifier.Camel))

  /**
   * Returns a builder for signals under `path`, split at the dots. Call the
   * builder with a name to get a signal, for example
   * `Signal.nested("user")[String]("name")`.
   */
  def nested(path: String): NestedBuilder =
    NestedBuilder(path.split('.').toList.filter(!_.isBlank))

  /**
   * Returns a builder for signals under `path` and `more`, each split at the
   * dots.
   */
  def nested(path: String, more: String*): NestedBuilder =
    NestedBuilder((path +: more).toList.flatMap(_.split('.')).filter(!_.isBlank))

  /** Helper for building nested signals with type inference */
  final case class NestedBuilder(path: List[String], caseModifier: CaseModifier = CaseModifier.Camel) {

    /** Returns a builder with `newPath` added in front of the current path. */
    def nest(newPath: String): NestedBuilder = copy(path = newPath :: path)

    /**
     * Returns a builder with `newPath` and `more` added in front of the current
     * path, each split at the dots.
     */
    def nest(newPath: String, more: String*): NestedBuilder =
      copy(path = ((newPath +: more) ++ path).toList.flatMap(_.split('.').toList))

    /**
     * Returns a builder that writes the signal names it creates with
     * `caseModifier`.
     */
    def caseModifier(caseModifier: CaseModifier): NestedBuilder =
      NestedBuilder(path, caseModifier)

    /**
     * Returns a signal of type `A` named `name` under the path of the builder.
     */
    def apply[A: Schema](name: String): Signal[A] = Signal(Schema[A], SignalName(path :+ name, caseModifier))

    /**
     * Returns a signal of type `A` named `name` under the path of the builder,
     * written with the given case modifier.
     */
    def apply[A: Schema](caseModifier: CaseModifier)(name: String): Signal[A] =
      Signal(Schema[A], SignalName(path :+ name, caseModifier))
  }

  /** Converts a signal to `Js` that holds its reference. */
  implicit def signalToJs[A](signal: Signal[A]): Js = js"$signal"
}

/**
 * The path of a signal and the case modifier that writes it. Construction
 * throws a `java.lang.AssertionError` when every segment of the path is blank
 * (`Signal name cannot be empty`) or when a segment contains two consecutive
 * underscores (`Signal names cannot contain double underscores '__'`).
 */
final case class SignalName(path: List[String], caseModifier: CaseModifier) { self =>
  assert(path.exists(!_.isBlank), "Signal name cannot be empty")
  assert(!path.exists(_.contains("__")), "Signal names cannot contain double underscores '__'")

  /** Returns a copy that writes the path with `newCaseModifier`. */
  def caseModifier(newCaseModifier: CaseModifier): SignalName =
    copy(caseModifier = newCaseModifier)

  /**
   * The path segments in kebab case, joined with `.`. This is the form used in
   * the key of attributes such as `data-signals:user.name`.
   */
  val name: String = path.map(CaseModifier.Kebab.modify).mkString(".")

  /**
   * The reference of the signal: `\$` followed by the path segments written
   * with the case modifier and joined with `.`.
   */
  val ref: String = s"$$${path.map(caseModifier.modify).mkString(".")}"

  /** Returns a signal of type `A` with this name. */
  def toSignal[A: Schema]: Signal[A] = Signal(self)

  /**
   * Returns an update that assigns `value` to the signal of type `A` with this
   * name.
   */
  def toSignalUpdate[A: Schema](value: A): SignalUpdate[A] = SignalUpdate(toSignal[A], value)

  /** Returns a name with `newPath` added in front of the path. */
  def nest(newPath: String): SignalName = copy(path = newPath :: path)

  /**
   * Returns a name with `newPath` and `more` added in front of the path, each
   * split at the dots.
   */
  def nest(newPath: String, more: String*): SignalName =
    copy(path = ((newPath +: more) ++ path).toList.flatMap(_.split('.').toList))
  private[datastar] def isRoot: Boolean                = path.length == 1
  override def toString: String                        = ref
}

/** Constructors for `SignalName`. */
object SignalName {

  /**
   * Returns a name from `path` and `more`, each split at the dots, written in
   * camel case.
   */
  def apply(path: String, more: String*): SignalName =
    SignalName((path +: more).toList.flatMap(_.split('.').toList), CaseModifier.Camel)

  /**
   * Returns a name from `path` and `more`, each split at the dots, written with
   * `caseModifier`.
   */
  def apply(caseModifier: CaseModifier)(path: String, more: String*): SignalName =
    SignalName((path +: more).toList.flatMap(_.split('.').toList), caseModifier)

  /** Converts a name to `Js` that holds its reference. */
  implicit def signalNameToJs(name: SignalName): Js = js"${name.ref}"
}

/**
 * Represents an assignment operation for updating a signal value. Can be used
 * with d-patch and other signal-updating attributes.
 */
final case class SignalUpdate[A](signal: Signal[A], value: A) {
  private implicit val codec: JsonCodec[A] = zio.schema.codec.JsonCodec.jsonCodec(signal.schema)

  /**
   * Render as a JSON object expression with the signal name/path included (for
   * data-signals attributes)
   */
  def toExpression: Js = {
    val ast    = value.toJsonAST.getOrElse(throw new RuntimeException("Failed to convert value to JSON AST"))
    val nested = signal.name.path.foldRight(ast) { (key, acc) =>
      zio.json.ast.Json.Obj(key -> acc)
    }
    js"${SignalUpdate.astToExpression(nested)}"
  }

  /**
   * Render as an assignment expression (for event handlers like dataOn.click)
   */
  private[http] def toAssignmentExpression: Js = {
    signal.name.path match {
      case _ :: Nil               =>
        // Root signal: $name = value
        val signalRef = signal.name.ref
        val valueStr  = signal.schema match {
          case _: Schema.Primitive[_] => value.toJson.replace("\"", "'")
          case _                      =>
            val ast = value.toJsonAST.getOrElse(throw new RuntimeException("Failed to convert value to JSON AST"))
            SignalUpdate.astToExpression(ast)
        }
        js"$signalRef = $valueStr"
      case outermost :: innerPath =>
        // Nested signal: $outermost = {inner: {path: value}}
        val ast           = value.toJsonAST.getOrElse(throw new RuntimeException("Failed to convert value to JSON AST"))
        val nestedObj     = innerPath.foldRight(ast) { (key, acc) =>
          zio.json.ast.Json.Obj(key -> acc)
        }
        val outermostRef  = s"$$${signal.name.caseModifier.modify(outermost)}"
        val nestedExprStr = SignalUpdate.astToExpression(nestedObj)
        js"$outermostRef = $nestedExprStr"
      case Nil                    =>
        throw new RuntimeException("Signal path cannot be empty")
    }
  }
}

/**
 * Holds the implicit conversion that turns a `SignalUpdate` into a `Js`
 * assignment such as `\$count = 5`.
 */
object SignalUpdate {
  private[signal] def astToExpression(ast: zio.json.ast.Json): String = ast match {
    case zio.json.ast.Json.Obj(fields) =>
      val fieldStrs = fields.map { case (k, v) => s"$k: ${astToExpression(v)}" }
      s"{${fieldStrs.mkString(", ")}}"
    case zio.json.ast.Json.Arr(items)  =>
      val itemStrs = items.map(astToExpression)
      s"[${itemStrs.mkString(", ")}]"
    case s: zio.json.ast.Json.Str      =>
      s"'${s.value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")}'"
    case zio.json.ast.Json.Num(value)  => value.toString
    case zio.json.ast.Json.Bool(value) => value.toString
    case zio.json.ast.Json.Null        => "null"
  }

  /**
   * Implicit conversion uses assignment expression format for use in event
   * handlers
   */
  implicit def signalUpdateToJs[A](update: SignalUpdate[A]): Js = update.toAssignmentExpression
}
