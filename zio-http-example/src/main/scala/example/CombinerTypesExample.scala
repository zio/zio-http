//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio.http.codec._

object CombinerTypesExample extends App {

  val foo = HttpCodec.query[String]("foo")
  val bar = HttpCodec.query[String]("bar")

  val combine1L1R: HttpCodec[HttpCodecType.Query, (String, String)]                 = foo & bar
  val combine1L2R: HttpCodec[HttpCodecType.Query, (String, String, String)]         = foo & (bar & bar)
  val combine2L1R: HttpCodec[HttpCodecType.Query, (String, String, String)]         = combine1L1R & bar
  val combine2L2R: HttpCodec[HttpCodecType.Query, (String, String, String, String)] = combine1L1R & (bar & bar)

  val combine2L3R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String)] =
    combine1L1R & (bar & bar & bar)

  val combine2L4R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String)] =
    combine1L1R & (bar & bar & bar & bar)

  val combine2L5R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String)] =
    combine1L1R & (bar & bar & bar & bar & bar)

  val combine2L6R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String)] =
    combine1L1R & (bar & bar & bar & bar & bar & bar)

  val combine2L7R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String)] = {
    combine1L1R & (bar & bar & bar & bar & bar & bar & bar)
  }

  val combine3L1R: HttpCodec[HttpCodecType.Query, (String, String, String, String)] = {
    combine2L1R & bar
  }

  val combine3L2R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String)] = {
    combine2L1R & (bar & bar)
  }

  val combine3L3R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String)] =
    combine2L1R & (bar & bar & bar)

  val combine3L4R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String)] =
    combine2L1R & (bar & bar & bar & bar)

  val combine3L5R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String)] =
    combine2L1R & (bar & bar & bar & bar & bar)

  val combine3L6R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String)] =
    combine2L1R & (bar & bar & bar & bar & bar & bar)

  val combine3L7R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String, String)] =
    combine2L1R & (bar & bar & bar & bar & bar & bar & bar)

  val combine3L8R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine2L1R & (bar & bar & bar & bar & bar & bar & bar & bar)

  val combine3L9R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine2L1R & (bar & bar & bar & bar & bar & bar & bar & bar & bar)

  val combine4L1R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String)] =
    combine3L1R & bar

  val combine4L2R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String)] =
    combine3L1R & (bar & bar)

  val combine4L3R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String)] =
    combine3L1R & (bar & bar & bar)

  val combine4L4R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String)] =
    combine3L1R & (bar & bar & bar & bar)

  val combine4L5R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String)] =
    combine3L1R & (bar & bar & bar & bar & bar)

  val combine4L6R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String, String)] =
    combine3L1R & (bar & bar & bar & bar & bar & bar)

  val combine4L7R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine3L1R & (bar & bar & bar & bar & bar & bar & bar)

  val combine4L8R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine3L1R & (bar & bar & bar & bar & bar & bar & bar & bar)

  val combine4L9R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine3L1R & (bar & bar & bar & bar & bar & bar & bar & bar & bar)

  val combine5L1R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String)] = combine4L1R & bar

  val combine5L2R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String)] =
    combine4L1R & (bar & bar)

  val combine5L3R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String)] =
    combine4L1R & (bar & bar & bar)

  val combine5L4R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String)] =
    combine4L1R & (bar & bar & bar & bar)

  val combine5L5R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String, String)] =
    combine4L1R & (bar & bar & bar & bar & bar)

  val combine5L6R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine4L1R & (bar & bar & bar & bar & bar & bar)

  val combine5L7R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine4L1R & (bar & bar & bar & bar & bar & bar & bar)

  val combine5L8R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine4L1R & (bar & bar & bar & bar & bar & bar & bar & bar)

  val combine5L9R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine4L1R & (bar & bar & bar & bar & bar & bar & bar & bar & bar)

  val combine6L1R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String)] =
    combine5L1R & bar

  val combine6L2R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String)] =
    combine5L1R & (bar & bar)

  val combine6L3R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String)] =
    combine5L1R & (bar & bar & bar)

  val combine6L4R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String, String)] =
    combine5L1R & (bar & bar & bar & bar)

  val combine6L5R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine5L1R & (bar & bar & bar & bar & bar)

  val combine6L6R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine5L1R & (bar & bar & bar & bar & bar & bar)

  val combine6L7R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine5L1R & (bar & bar & bar & bar & bar & bar & bar)

  val combine6L8R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine5L1R & (bar & bar & bar & bar & bar & bar & bar & bar)

  val combine6L9R: HttpCodec[
    HttpCodecType.Query,
    (
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
    ),
  ] =
    combine5L1R & (bar & bar & bar & bar & bar & bar & bar & bar & bar)

  val combine7L1R: HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String)] =
    combine6L1R & bar

  val combine7L2R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String)] =
    combine6L1R & (bar & bar)

  val combine7L3R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String, String)] =
    combine6L1R & (bar & bar & bar)

  val combine7L4R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine6L1R & (bar & bar & bar & bar)

  val combine7L5R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine6L1R & (bar & bar & bar & bar & bar)

  val combine7L6R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine6L1R & (bar & bar & bar & bar & bar & bar)

  val combine7L7R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine6L1R & (bar & bar & bar & bar & bar & bar & bar)

  val combine7L8R: HttpCodec[
    HttpCodecType.Query,
    (
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
    ),
  ] =
    combine6L1R & (bar & bar & bar & bar & bar & bar & bar & bar)

  val combine8L1R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String)] =
    combine7L1R & bar

  val combine8L2R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String, String)] =
    combine7L1R & (bar & bar)

  val combine8L3R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine7L1R & (bar & bar & bar)

  val combine8L4R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine7L1R & (bar & bar & bar & bar)

  val combine8L5R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine7L1R & (bar & bar & bar & bar & bar)

  val combine8L6R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine7L1R & (bar & bar & bar & bar & bar & bar)

  val combine8L7R: HttpCodec[
    HttpCodecType.Query,
    (
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
    ),
  ] =
    combine7L1R & (bar & bar & bar & bar & bar & bar & bar)

  val combine9L1R
    : HttpCodec[HttpCodecType.Query, (String, String, String, String, String, String, String, String, String, String)] =
    combine8L1R & bar

  val combine9L2R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine8L1R & (bar & bar)

  val combine9L3R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine8L1R & (bar & bar & bar)

  val combine9L4R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine8L1R & (bar & bar & bar & bar)

  val combine9L5R: HttpCodec[
    HttpCodecType.Query,
    (String, String, String, String, String, String, String, String, String, String, String, String, String, String),
  ] =
    combine8L1R & (bar & bar & bar & bar & bar)

  val combine9L6R: HttpCodec[
    HttpCodecType.Query,
    (
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
    ),
  ] =
    combine8L1R & (bar & bar & bar & bar & bar & bar)

  val combine9L7R: HttpCodec[
    HttpCodecType.Query,
    (
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
      String,
    ),
  ] =
    combine8L1R & (bar & bar & bar & bar & bar & bar & bar)

}
