package zio.http.endpoint.internal

import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.model._
import zio.schema.codec._

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
import scala.annotation.tailrec

private[endpoint] trait EncoderDecoder[-AtomTypes, Value] {
  def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
    trace: Trace,
  ): Task[Value]

  def encodeWith[Z](value: Value)(f: (URL, Option[Status], Option[Method], Headers, Body) => Z): Z
}
private[endpoint] object EncoderDecoder                   {
  def apply[AtomTypes, Value](httpCodec: HttpCodec[AtomTypes, Value]): EncoderDecoder[AtomTypes, Value] = {
    val flattened = httpCodec.alternatives

    if (flattened.length == 1) Single(flattened.head)
    else Multiple(flattened)
  }

  private final case class Multiple[-AtomTypes, Value](httpCodecs: Chunk[HttpCodec[AtomTypes, Value]])
      extends EncoderDecoder[AtomTypes, Value] {
    val singles = httpCodecs.map(Single(_))
    val head    = singles.head
    val tail    = singles.tail

    def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
      trace: Trace,
    ): Task[Value] = {
      def tryDecode(i: Int, lastError: Cause[Throwable]): Task[Value] = {
        if (i >= singles.length) ZIO.refailCause(lastError)
        else {
          val codec = singles(i)

          codec
            .decode(url, status, method, headers, body)
            .catchAllCause(cause =>
              if (shouldRetry(cause)) {
                tryDecode(i + 1, lastError && cause)
              } else ZIO.refailCause(cause),
            )
        }
      }

      tryDecode(0, Cause.empty)
    }

    def encodeWith[Z](value: Value)(f: (URL, Option[Status], Option[Method], Headers, Body) => Z): Z = {
      var i         = 0
      var encoded   = null.asInstanceOf[Z]
      var lastError = null.asInstanceOf[Throwable]

      while (i < singles.length) {
        val current = singles(i)

        try {
          encoded = current.encodeWith(value)(f)

          i = singles.length // break
        } catch {
          case error: EndpointError =>
            // TODO: Aggregate all errors in disjunction:
            lastError = error
        }

        i = i + 1
      }

      if (encoded == null) throw lastError
      else encoded
    }

    private def shouldRetry(cause: Cause[Any]): Boolean =
      !cause.isFailure && cause.defects.forall(_.isInstanceOf[EndpointError])
  }

  private final case class Single[-AtomTypes, Value](httpCodec: HttpCodec[AtomTypes, Value])
      extends EncoderDecoder[AtomTypes, Value] {
    private val constructor   = Mechanic.makeConstructor(httpCodec)
    private val deconstructor = Mechanic.makeDeconstructor(httpCodec)

    private val flattened: AtomizedCodecs = Mechanic.flatten(httpCodec)

    private val jsonEncoders = flattened.body.map { bodyCodec =>
      val erased    = bodyCodec.erase
      val jsonCodec = JsonCodec.schemaBasedBinaryCodec(erased.schema)
      erased.encodeToBody(_, jsonCodec)
    }
    private val jsonDecoders = flattened.body.map { bodyCodec =>
      val jsonCodec = JsonCodec.schemaBasedBinaryCodec(bodyCodec.schema)
      bodyCodec.decodeFromBody(_, jsonCodec)
    }

    def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
      trace: Trace,
    ): Task[Value] = ZIO.suspendSucceed {
      val inputsBuilder = flattened.makeInputsBuilder()

      decodePaths(url.path, inputsBuilder.path)
      decodeQuery(url.queryParams, inputsBuilder.query)
      decodeStatus(status, inputsBuilder.status)
      decodeMethod(method, inputsBuilder.method)
      decodeHeaders(headers, inputsBuilder.header)
      decodeBody(body, inputsBuilder.body).as(constructor(inputsBuilder))
    }

    final def encodeWith[Z](value: Value)(f: (URL, Option[Status], Option[Method], Headers, Body) => Z): Z = {
      val inputs = deconstructor(value)

      val path    = encodePath(inputs.path)
      val query   = encodeQuery(inputs.query)
      val status  = encodeStatus(inputs.status)
      val method  = encodeMethod(inputs.method)
      val headers = encodeHeaders(inputs.header)
      val body    = encodeBody(inputs.body)

      f(URL(path, queryParams = query), status, method, headers, body)
    }

    private def decodePaths(path: Path, inputs: Array[Any]): Unit = {
      assert(flattened.path.length == inputs.length)

      var i        = 0
      var j        = 0
      val segments = path.segments
      while (i < inputs.length) {
        val textCodec = flattened.path(i).erase

        if (j >= segments.length) throw EndpointError.PathTooShort(path, textCodec)
        else {
          val segment = segments(j)

          if (segment.text.length != 0) {
            val textCodec = flattened.path(i).erase

            inputs(i) =
              textCodec.decode(segment.text).getOrElse(throw EndpointError.MalformedPath(path, segment, textCodec))

            i = i + 1
          }
          j = j + 1
        }
      }
    }

    private def decodeQuery(queryParams: QueryParams, inputs: Array[Any]): Unit = {
      var i       = 0
      val queries = flattened.query
      while (i < queries.length) {
        val query = queries(i).erase

        val queryParamValue =
          queryParams
            .getOrElse(query.name, Nil)
            .collectFirst(query.textCodec)

        queryParamValue match {
          case Some(value) =>
            inputs(i) = value
          case None        =>
            throw EndpointError.MissingQueryParam(query.name)
        }

        i = i + 1
      }
    }

    private def decodeStatus(status: Status, inputs: Array[Any]): Unit = {
      var i = 0
      while (i < inputs.length) {
        val textCodec = flattened.status(i).erase

        inputs(i) = textCodec.decode(status.text).getOrElse(throw EndpointError.MalformedStatus("200", textCodec))

        i = i + 1
      }
    }

    private def decodeMethod(method: Method, inputs: Array[Any]): Unit = {
      var i = 0
      while (i < inputs.length) {
        val textCodec = flattened.method(i)

        inputs(i) =
          textCodec.decode(method.text).getOrElse(throw EndpointError.MalformedMethod(method.toString(), textCodec))

        i = i + 1
      }
    }

    private def decodeHeaders(headers: Headers, inputs: Array[Any]): Unit = {
      var i = 0
      while (i < flattened.header.length) {
        val header = flattened.header(i).erase

        headers.get(header.name) match {
          case Some(value) =>
            inputs(i) = header.textCodec
              .decode(value)
              .getOrElse(throw EndpointError.MalformedHeader(header.name, header.textCodec))

          case None =>
            throw EndpointError.MissingHeader(header.name)
        }

        i = i + 1
      }
    }

    private def decodeBody(body: Body, inputs: Array[Any])(implicit trace: Trace): Task[Unit] =
      if (jsonDecoders.length == 0) ZIO.unit
      else if (jsonDecoders.length == 1) {
        jsonDecoders(0)(body).map { result => inputs(0) = result }
      } else {
        ZIO.foreachDiscard(jsonDecoders.zipWithIndex) { case (decoder, index) =>
          decoder(body).map { result => inputs(index) = result }
        }
      }

    private def encodePath(inputs: Array[Any]): Path = {
      var path = Path.empty

      var i = 0
      while (i < inputs.length) {
        val textCodec = flattened.path(i).erase
        val input     = inputs(i)

        val segment = textCodec.encode(input)

        path = path / segment
        i = i + 1
      }

      path
    }

    private def encodeQuery(inputs: Array[Any]): QueryParams = {
      var queryParams = QueryParams.empty

      var i = 0
      while (i < inputs.length) {
        val query = flattened.query(i).erase
        val input = inputs(i)

        val value = query.textCodec.encode(input)

        queryParams = queryParams.add(query.name, value)

        i = i + 1
      }

      queryParams
    }

    private def encodeStatus(inputs: Array[Any]): Option[Status] = {
      if (flattened.status.length == 0) {
        None
      } else {
        val statusString = flattened.status(0).erase.encode(inputs(0))

        Some(Status.fromInt(statusString.toInt).getOrElse(Status.Ok))
      }
    }

    private def encodeHeaders(inputs: Array[Any]): Headers = {
      var headers = Headers.contentType("application/json")

      var i = 0
      while (i < inputs.length) {
        val header = flattened.header(i).erase
        val input  = inputs(i)

        val value = header.textCodec.encode(input)

        headers = headers ++ Headers(header.name, value)

        i = i + 1
      }

      headers
    }

    private def encodeMethod(inputs: Array[Any]): Option[zio.http.model.Method] = {
      if (flattened.method.nonEmpty) {
        val method = flattened.method.head.erase
        Some(zio.http.model.Method.fromString(method.encode(inputs(0))))
      } else {
        None
      }
    }

    private def encodeBody(inputs: Array[Any]): Body = {
      if (jsonEncoders.length == 0) Body.empty
      else if (jsonEncoders.length == 1) {
        val encoder = jsonEncoders(0)

        encoder(inputs(0))
      } else throw new IllegalStateException("A request on a REST endpoint should have at most one body")
    }
  }
}
