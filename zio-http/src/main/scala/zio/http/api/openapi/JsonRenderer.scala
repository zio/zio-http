package zio.http.api.openapi

import zio.http.api.Doc
import zio.http.model.Status

import java.net.URI
import scala.collection.immutable.Iterable

private[openapi] object JsonRenderer {

  def renderFields(fieldsIt: (String, Any)*): String = {
    if (fieldsIt.map(_._1).toSet.size != fieldsIt.size) {
      throw new IllegalArgumentException("Duplicate field names")
    } else {
      val fields = fieldsIt
        .filterNot(_._2 == None) // We don't render empty fields
        .map { case (name, value) => s""""$name":${renderValue(value)}""" }
      s"{${fields.mkString(",")}}"
    }
  }

  private def renderValue(value: Any): String = value match {
    case Some(value)                       => renderValue(value)
    case o: OpenAPIBase                        => o.toJson
    case s: Status                         => s.code.toString
    case s: String                         => s""""$s""""
    case n: Int                            => n.toString
    case n: Double                         => n.toString
    case n: Long                           => n.toString
    case n: Float                          => n.toString
    case d: Doc                            => renderDoc(d)
    case b: Boolean                        => b.toString
    case m: Map[_, _]                      => renderMap(m)
    case l: Iterable[Any]                  => renderIterable(l)
    case (k, v)                            => s"{${renderKey(k)}:${renderValue(v)}}"
    case u: URI                            => s""""${u.toString}""""
    case p: Product if p.productArity == 0 => renderSingleton(p)
    case other                             => s""""${other.toString}""""
  }

  private def renderDoc(d: Doc) =
    // todo render markdown
    s""""${d.toPlaintext(color = false)}"""".replace("\n", "<br/>")

  private def renderIterable(l: Iterable[_]) =
    s"[${l.map(renderValue).mkString(",")}]"

  private def renderMap(m: Map[_ <: Any, _ <: Any]) =
    s"{${m.toList.map { case (k, v) => s"${renderKey(k)}:${renderValue(v)}" }.mkString(",")}}"

  private def renderKey(k: Any) =
    if (renderValue(k).startsWith("\"") && renderValue(k).endsWith("\"")) renderValue(k)
    else s""""${renderValue(k)}""""

  private def renderSingleton(p: Product) =
    s""""${p.productPrefix.updated(0, p.productPrefix.charAt(0).toLower)}""""
}
