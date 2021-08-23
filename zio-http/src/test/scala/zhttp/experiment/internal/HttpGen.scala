package zhttp.experiment.internal

import io.netty.buffer.Unpooled
import zhttp.experiment.{Content, HttpMessage, ServerEndpoint}
import zhttp.http.{Header, Status}
import zio.stream.ZStream
import zio.test.Gen

object HttpGen {
  val status: Gen[Any, Status] = Gen.fromIterable(
    List(
      Status.CONTINUE,
      Status.SWITCHING_PROTOCOLS,
      Status.PROCESSING,
      Status.OK,
      Status.CREATED,
      Status.ACCEPTED,
      Status.NON_AUTHORITATIVE_INFORMATION,
      Status.NO_CONTENT,
      Status.RESET_CONTENT,
      Status.PARTIAL_CONTENT,
      Status.MULTI_STATUS,
      Status.MULTIPLE_CHOICES,
      Status.MOVED_PERMANENTLY,
      Status.FOUND,
      Status.SEE_OTHER,
      Status.NOT_MODIFIED,
      Status.USE_PROXY,
      Status.TEMPORARY_REDIRECT,
      Status.PERMANENT_REDIRECT,
      Status.BAD_REQUEST,
      Status.UNAUTHORIZED,
      Status.PAYMENT_REQUIRED,
      Status.FORBIDDEN,
      Status.NOT_FOUND,
      Status.METHOD_NOT_ALLOWED,
      Status.NOT_ACCEPTABLE,
      Status.PROXY_AUTHENTICATION_REQUIRED,
      Status.REQUEST_TIMEOUT,
      Status.CONFLICT,
      Status.GONE,
      Status.LENGTH_REQUIRED,
      Status.PRECONDITION_FAILED,
      Status.REQUEST_ENTITY_TOO_LARGE,
      Status.REQUEST_URI_TOO_LONG,
      Status.UNSUPPORTED_MEDIA_TYPE,
      Status.REQUESTED_RANGE_NOT_SATISFIABLE,
      Status.EXPECTATION_FAILED,
      Status.MISDIRECTED_REQUEST,
      Status.UNPROCESSABLE_ENTITY,
      Status.LOCKED,
      Status.FAILED_DEPENDENCY,
      Status.UNORDERED_COLLECTION,
      Status.UPGRADE_REQUIRED,
      Status.PRECONDITION_REQUIRED,
      Status.TOO_MANY_REQUESTS,
      Status.REQUEST_HEADER_FIELDS_TOO_LARGE,
      Status.INTERNAL_SERVER_ERROR,
      Status.NOT_IMPLEMENTED,
      Status.BAD_GATEWAY,
      Status.SERVICE_UNAVAILABLE,
      Status.GATEWAY_TIMEOUT,
      Status.HTTP_VERSION_NOT_SUPPORTED,
      Status.VARIANT_ALSO_NEGOTIATES,
      Status.INSUFFICIENT_STORAGE,
      Status.NOT_EXTENDED,
      Status.NETWORK_AUTHENTICATION_REQUIRED,
    ),
  )

  def content[R](gen: Gen[R, List[String]]) =
    for {
      list <- gen
      cnt  <- Gen
        .fromIterable(
          List(
            Content.fromStream(ZStream.fromIterable(list).map(b => Unpooled.copiedBuffer(b.getBytes()))),
            Content.complete(Unpooled.copiedBuffer(list.mkString("").getBytes())),
            Content.empty,
          ),
        )
    } yield cnt

  def nonEmptyContent[R](gen: Gen[R, List[String]]) =
    for {
      list <- gen
      cnt  <- Gen
        .fromIterable(
          List(
            Content.fromStream(ZStream.fromIterable(list).map(b => Unpooled.copiedBuffer(b.getBytes()))),
            Content.complete(Unpooled.copiedBuffer(list.mkString("").getBytes())),
          ),
        )
    } yield cnt

  def header = for {
    key   <- Gen.alphaNumericStringBounded(1, 4)
    value <- Gen.alphaNumericStringBounded(1, 4)
  } yield Header(key, value)

  def response[R](gContent: Gen[R, List[String]]) =
    for {
      content <- HttpGen.content(gContent)
      headers <- HttpGen.header.map(List(_))
      status  <- HttpGen.status
    } yield HttpMessage.AnyResponse(status, headers, content)

  def canDecode = Gen.fromIterable(
    List(
      ServerEndpoint.CanDecode.MountAnything,
      ServerEndpoint.CanDecode.MountComplete,
      ServerEndpoint.CanDecode.MountBuffered,
      ServerEndpoint.CanDecode.MountAnyRequest,
    ),
  )
}
