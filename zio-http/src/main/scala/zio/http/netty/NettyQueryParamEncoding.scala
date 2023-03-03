package zio.http.netty

import scala.jdk.CollectionConverters._

import zio.Chunk

import zio.http.QueryParams
import zio.http.internal.QueryParamEncoding

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}

private[http] object NettyQueryParamEncoding extends QueryParamEncoding {
  override final def decode(queryStringFragment: String): QueryParams = {
    if (queryStringFragment == null || queryStringFragment.isEmpty) {
      QueryParams.empty
    } else {
      val decoder = new QueryStringDecoder(queryStringFragment, false)
      val params  = decoder.parameters()
      QueryParams(params.asScala.view.map { case (k, v) =>
        (k, Chunk.fromIterable(v.asScala))
      }.toMap)
    }
  }

  override final def encode(baseUri: String, queryParams: QueryParams): String = {
    val encoder = new QueryStringEncoder(baseUri)
    queryParams.map.foreach { case (key, values) =>
      if (key != "") {
        if (values.isEmpty) {
          encoder.addParam(key, "")
        } else
          values.foreach(value => encoder.addParam(key, value))
      }
    }

    encoder.toString
  }
}
