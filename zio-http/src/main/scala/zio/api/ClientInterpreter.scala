package zhttp.api

import zhttp.http.{HttpData, Response}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.ZIO
import zio.schema.Schema
import zio.schema.codec.JsonCodec

import scala.collection.mutable

private[api] object ClientInterpreter {
  def interpret[Params, Input, Output](host: String)(
    api: API[Params, Input, Output],
  )(params: Params, input: Input): ZIO[EventLoopGroup with ChannelFactory, Throwable, Response] = {
    val state          = new RequestState()
    parseUrl(api.requestCodec, state)(params)
    val (url, headers) = state.result
    val data           =
      if (api.inputSchema == Schema[Unit]) HttpData.empty
      else HttpData.fromChunk(JsonCodec.encode(api.inputSchema)(input))

    Client.request(s"$host$url", api.method, zhttp.http.Headers(headers.toList), content = data)
  }

  private[api] class RequestState {
    private val query: mutable.Map[String, String]   = mutable.Map.empty
    private val headers: mutable.Map[String, String] = mutable.Map.empty
    private val pathBuilder: StringBuilder           = new StringBuilder()

    def addPath(path: String): Unit =
      pathBuilder.addAll(path)

    def addQuery(key: String, value: String): Unit = {
      val _ = query.put(key, value)
    }

    def addHeader(key: String, value: String): Unit = {
      val _ = headers.put(key, value)
    }

    def result: (String, Map[String, String]) = {

      val queryString =
        if (query.nonEmpty)
          query.map { case (key, value) => s"$key=$value" }.mkString("?", "&", "")
        else
          ""

      (pathBuilder.result() + queryString, headers.toMap)
    }
  }

  private[api] def parseUrl[Params](
    requestCodec: RequestCodec[Params],
    state: RequestState,
  )(params: Params): Unit =
    requestCodec match {
      case RequestCodec.ZipWith(left, right, _, g) =>
        g(params) match {
          case (a, b) =>
            parseUrl(left, state)(a)
            parseUrl(right, state)(b)
        }
      case RequestCodec.Map(info, _, g)            =>
        parseUrl(info, state)(g(params))

      case headers: Header[_] =>
        parseHeaders[Params](headers, state)(params)

      case query: Query[_] =>
        parseQuery[Params](query, state)(params)

      case route: Route[_] =>
        parsePath[Params](route, state)(params)
    }

  private def parsePath[Params](
    route: Route[Params],
    state: RequestState,
  )(params: Params): Unit =
    route match {
      case Route.MapRoute(route, _, g)      =>
        parsePath(route, state)(g(params))
      case Route.ZipWith(left, right, _, g) =>
        g(params) match {
          case (a, b) =>
            parsePath(left, state)(a)
            parsePath(right, state)(b)
        }
      case Route.Literal(literal)           =>
        state.addPath("/" + literal)
      case Route.Match(_, _, _)             =>
        state.addPath("/" + params.toString)
    }

  private def parseQuery[Params](
    query: Query[Params],
    state: RequestState,
  )(params: Params): Unit =
    query match {
      case Query.SingleParam(name, _, _) =>
        state.addQuery(name, params.toString)
      case Query.Optional(p)             =>
        params match {
          case Some(params) =>
            parseQuery(p, state)(params)
          case None         =>
            ()
          case params       =>
            throw new Error(s"THIS SHOULD NOT HAPPEN. Unexpected params: $params")
        }

      case Query.ZipWith(left, right, _, g) =>
        g(params) match {
          case (a, b) =>
            parseQuery(left, state)(a)
            parseQuery(right, state)(b)
        }

      case Query.MapParams(info, _, g) =>
        parseQuery(info, state)(g(params))
    }

  private def parseHeaders[Params](
    headers: Header[Params],
    state: RequestState,
  )(params: Params): Unit =
    headers match {
      case Header.Map(headers, _, g)         =>
        parseHeaders(headers, state)(g(params))
      case Header.ZipWith(left, right, _, g) =>
        g(params) match {
          case (a, b) =>
            parseHeaders(left, state)(a)
            parseHeaders(right, state)(b)
        }
      case Header.Optional(headers)          =>
        params match {
          case Some(params) =>
            parseHeaders(headers, state)(params)
          case None         =>
            ()
          case params       =>
            throw new Error(s"THIS SHOULD NOT HAPPEN. Unexpected params: $params")
        }
      case Header.SingleHeader(name, _)      =>
        state.addHeader(name, params.toString)
    }
}
