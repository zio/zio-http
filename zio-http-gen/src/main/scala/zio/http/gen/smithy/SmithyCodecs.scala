package zio.http.gen.smithy

import zio.Chunk

import zio.http.codec.RichTextCodec

/**
 * Smithy IDL primitive codecs using RichTextCodec.
 *
 * These provide the building blocks for parsing Smithy IDL. The primitives
 * demonstrate the enhanced | operator with Alternator support that unifies
 * same-type alternatives without nested Either types.
 */
object SmithyCodecs {

  /** Single space or tab */
  val sp: RichTextCodec[Unit] =
    RichTextCodec.filter(c => c == ' ' || c == '\t').const(' ')

  /** Newline (LF or CRLF) */
  val newline: RichTextCodec[Unit] = {
    // CRLF: \r\n - Combiner with Unit simplifies Unit ~ Unit to Unit
    val crlf = (RichTextCodec.char('\r').const('\r') ~ RichTextCodec.char('\n').const('\n'))
      .transform(_ => ())(_ => ())
    val lf   = RichTextCodec.char('\n').const('\n')
    crlf | lf
  }

  /** Comma - treated as whitespace in Smithy */
  val comma: RichTextCodec[Unit] =
    RichTextCodec.char(',').const(',')

  /** Basic whitespace character (space, tab, newline, comma) */
  val wsChar: RichTextCodec[Unit] =
    sp | newline | comma

  /** Zero or more whitespace characters */
  val ws: RichTextCodec[Unit] =
    wsChar.repeat.transform(_ => ())(_ => Chunk.empty)

  /** One or more whitespace characters */
  val ws1: RichTextCodec[Unit] =
    // Combiner with Unit simplifies Unit ~ Unit to Unit
    (wsChar ~ ws).transform(_ => ())(_ => ())

  /** Identifier: [a-zA-Z_][a-zA-Z0-9_]* */
  val identifier: RichTextCodec[String] = {
    val start = RichTextCodec.filter(c => c.isLetter || c == '_')
    val rest  = RichTextCodec.filter(c => c.isLetterOrDigit || c == '_').repeat
    (start ~ rest).transform { case (head, tail) =>
      (head +: tail).mkString
    } { s =>
      (s.head, Chunk.fromArray(s.tail.toCharArray))
    }
  }

  /** Namespace: identifier(.identifier)* */
  val namespace: RichTextCodec[String] = {
    val dot   = RichTextCodec.char('.').const('.')
    val parts = (dot ~> identifier).repeat
    (identifier ~ parts).transform { case (first, rest) =>
      (first +: rest).mkString(".")
    } { s =>
      val ps = s.split("\\.")
      (ps.head, Chunk.fromArray(ps.tail))
    }
  }

  /** Quoted string with escape sequences */
  val quotedString: RichTextCodec[String] = {
    val quoteOpen  = RichTextCodec.char('"').const('"')
    val quoteClose = RichTextCodec.char('"').const('"')

    val backslash                        = RichTextCodec.char('\\').const('\\')
    val escapedChar: RichTextCodec[Char] = (backslash ~> RichTextCodec.filter(_ => true)).transform {
      case 'n'  => '\n'
      case 'r'  => '\r'
      case 't'  => '\t'
      case '\\' => '\\'
      case '"'  => '"'
      case c    => c
    } {
      case '\n' => 'n'
      case '\r' => 'r'
      case '\t' => 't'
      case '\\' => '\\'
      case '"'  => '"'
      case c    => c
    }

    val regularChar: RichTextCodec[Char] = RichTextCodec.filter(c => c != '"' && c != '\\')
    val stringChar: RichTextCodec[Char]  = escapedChar | regularChar
    val content                          = stringChar.repeat.string

    quoteOpen ~> content <~ quoteClose
  }

  /** Integer number (possibly negative) */
  val integerNumber: RichTextCodec[Long] = {
    val minus  = RichTextCodec.char('-')
    val digits = RichTextCodec.filter(_.isDigit).repeat
    (minus.repeat ~ digits).transform { case (minuses, digitChars) =>
      val numStr   = digitChars.mkString
      val negative = minuses.nonEmpty
      val value    = if (numStr.isEmpty) 0L else numStr.toLong
      if (negative) -value else value
    } { n =>
      val abs    = math.abs(n)
      val chars  = Chunk.fromArray(abs.toString.toCharArray)
      val prefix = if (n < 0) Chunk.single('-') else Chunk.empty[Char]
      (prefix, chars)
    }
  }

  /** Decimal number (with optional decimal point) */
  val decimalNumber: RichTextCodec[BigDecimal] = {
    val minus      = RichTextCodec.char('-')
    val digit      = RichTextCodec.filter(_.isDigit)
    val digits     = digit.repeat
    val dot        = RichTextCodec.char('.')
    val fractional = (dot ~ digits).transform { case (_, frac) =>
      "." + frac.mkString
    } { s =>
      ('.', Chunk.fromArray(s.drop(1).toCharArray))
    }
    val optFrac    = fractional | RichTextCodec.empty.as("")

    (minus.repeat ~ digits ~ optFrac).transform { case (minuses, intDigits, frac) =>
      val numStr   = intDigits.mkString + frac
      val negative = minuses.nonEmpty
      val value    = if (numStr.isEmpty || numStr == ".") BigDecimal(0) else BigDecimal(numStr)
      if (negative) -value else value
    } { n =>
      val str            = n.abs.toString()
      val dotIdx         = str.indexOf('.')
      val (intPart, dec) = if (dotIdx >= 0) (str.take(dotIdx), str.drop(dotIdx)) else (str, "")
      val prefix         = if (n < 0) Chunk.single('-') else Chunk.empty[Char]
      (prefix, Chunk.fromArray(intPart.toCharArray), dec)
    }
  }

  /** ShapeId: `[namespace#]name[$$member]` */
  val shapeId: RichTextCodec[ShapeId] = {
    val hash   = RichTextCodec.char('#').const('#')
    val dollar = RichTextCodec.char('$').const('$')

    val nsAndHash = (namespace <~ hash).transform(ns => Some(ns): Option[String]) {
      case Some(ns) => ns
      case None     => ""
    }
    val optNs     = nsAndHash | RichTextCodec.empty.as(Option.empty[String])

    val memberPart = (dollar ~> identifier).transform(m => Some(m): Option[String]) {
      case Some(m) => m
      case None    => ""
    }
    val optMember  = memberPart | RichTextCodec.empty.as(Option.empty[String])

    (optNs ~ identifier ~ optMember).transform { case (ns, name, member) =>
      ShapeId(ns, name, member)
    } { id =>
      (id.namespace, id.name, id.member)
    }
  }

  /** Node string value */
  val nodeString: RichTextCodec[NodeValue] =
    quotedString.transform[NodeValue](NodeValue.Str(_)) {
      case NodeValue.Str(s) => s
      case _                => ""
    }

  /** Node number value */
  val nodeNumber: RichTextCodec[NodeValue] =
    decimalNumber.transform[NodeValue](NodeValue.Num(_)) {
      case NodeValue.Num(n) => n
      case _                => BigDecimal(0)
    }

  /** Node boolean value */
  val nodeBool: RichTextCodec[NodeValue] = {
    val t: RichTextCodec[NodeValue] =
      RichTextCodec.literal("true").transform(_ => NodeValue.Bool(true): NodeValue)(_ => "true")
    val f: RichTextCodec[NodeValue] =
      RichTextCodec.literal("false").transform(_ => NodeValue.Bool(false): NodeValue)(_ => "false")
    t | f
  }

  /** Node null value */
  val nodeNull: RichTextCodec[NodeValue] =
    RichTextCodec.literal("null").transform(_ => NodeValue.Null: NodeValue)(_ => "null")

  /** Node shape ID value */
  val nodeShapeId: RichTextCodec[NodeValue] =
    shapeId.transform[NodeValue](NodeValue.ShapeIdValue(_)) {
      case NodeValue.ShapeIdValue(id) => id
      case _                          => ShapeId("Unknown")
    }

  /** Simple shape keyword - unified type thanks to Alternator */
  val simpleShapeKeyword: RichTextCodec[String] = {
    val keywords = List(
      "bigDecimal",
      "bigInteger",
      "timestamp",
      "document",
      "boolean",
      "integer",
      "double",
      "string",
      "short",
      "float",
      "blob",
      "byte",
      "long",
    )
    keywords.map(RichTextCodec.literal).reduce(_ | _)
  }

}
