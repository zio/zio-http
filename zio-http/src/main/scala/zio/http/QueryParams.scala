package zio.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}
import zio.Chunk

import scala.jdk.CollectionConverters._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class QueryParams private[http] (map: Map[String, Chunk[String]])
    extends scala.collection.Map[String, Chunk[String]] {
  self =>

  override def -(key: String): QueryParams = QueryParams(map - key)

  override def -(key1: String, key2: String, keys: String*): QueryParams =
    QueryParams(map.--(Chunk(key1, key2) ++ keys))

  override def +[V1 >: Chunk[String]](kv: (String, V1)): Map[String, V1] =
    map.+(kv)

  def ++(other: QueryParams): QueryParams =
    QueryParams((Chunk.fromIterable(map) ++ Chunk.fromIterable(other.map)).groupBy(_._1).map { case (key, values) =>
      (key, values.flatMap(_._2))
    })

  def add(key: String, value: String): QueryParams =
    addAll(key, Chunk(value))

  def addAll(key: String, value: Chunk[String]): QueryParams = {
    val previousValue = map.get(key)
    val newValue      = previousValue match {
      case Some(prev) => prev ++ value
      case None       => value
    }
    QueryParams(map.updated(key, newValue))
  }

  def encode: String = {
    val encoder = new QueryStringEncoder(s"")
    map.foreach { case (key, values) =>
      if (key != "") {
        if (values.isEmpty) {
          encoder.addParam(key, "")
        } else
          values.foreach(value => encoder.addParam(key, value))
      }
    }

    encoder.toString
  }

  def toMap: Map[String, Chunk[String]] = map

  override def get(key: String): Option[Chunk[String]] = map.get(key)

  override def iterator: Iterator[(String, Chunk[String])] = map.iterator

}

object QueryParams {

  def apply(tuples: (String, Chunk[String])*): QueryParams =
    QueryParams(map = Chunk.fromIterable(tuples).groupBy(_._1).map { case (key, values) =>
      key -> values.flatMap(_._2)
    })

  def apply(tuple1: (String, String), tuples: (String, String)*): QueryParams =
    QueryParams(map = Chunk.fromIterable(tuple1 +: tuples.toVector).groupBy(_._1).map { case (key, values) =>
      key -> values.map(_._2)
    })

  def decode(queryStringFragment: String): QueryParams =
    if (queryStringFragment == null || queryStringFragment.isEmpty) {
      QueryParams.empty
    } else {
      val decoder = new QueryStringDecoder(queryStringFragment, false)
      val params  = decoder.parameters()
      QueryParams(params.asScala.view.map { case (k, v) =>
        (k, Chunk.fromIterable(v.asScala))
      }.toMap)
    }

  val empty: QueryParams = QueryParams(Map.empty[String, Chunk[String]])

}
