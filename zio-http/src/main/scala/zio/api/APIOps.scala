package zhttp.api

import zhttp.api.API.NotUnit
import zhttp.http.HttpApp
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.ZIO
import zio.schema.Schema
import zio.schema.codec.JsonCodec

final class APIOps[Params, Input, Output: NotUnit, ZipOut, Id](
  val self: API.WithId[Params, Input, Output, Id],
)(implicit
  zipper: Zipper.WithOut[Params, Input, ZipOut],
) {
  def handle[R, E](
    f: ZipOut => ZIO[R, E, Output],
  ): Handler[R, E, Params, Input, Output] =
    Handler.make[R, E, Params, Input, Output](self) { case (params, input) =>
      f(zipper.zip(params, input)).map(ApiResponse(_))
    }

  def toHttp[R, E](
    f: ZipOut => ZIO[R, E, Output],
  ): HttpApp[R, E] =
    handle[R, E](f).toHttp

  def call(host: String)(params: ZipOut): ZIO[EventLoopGroup with ChannelFactory, Throwable, Output] = {
    val tuple = zipper.unzip(params)
    ClientInterpreter.interpret(host)(self)(tuple._1, tuple._2).flatMap(_.body).flatMap { string =>
      JsonCodec.decode(self.outputSchema)(string) match {
        case Left(err)    => ZIO.fail(new Error(s"Could not parse response: $err"))
        case Right(value) => ZIO.succeed(value)
      }
    }
  }
}

final class APIOpsUnit[Params, Input, ZipOut, Id](val self: API.WithId[Params, Input, Unit, Id])(implicit
  zipper: Zipper.WithOut[Params, Input, ZipOut],
) {
  def handle[R, E, Output2](
    f: ZipOut => ZIO[R, E, Output2],
  )(implicit
    schema: Schema[Output2],
  ): Handler[R, E, Params, Input, Output2] =
    Handler
      .make[R, E, Params, Input, Output2](self.output[Output2]) { case (params, input) =>
        f(zipper.zip(params, input)).map(ApiResponse(_))
      }

  def handleWith[R, E, Output2](
    f: ZipOut => ZIO[R, E, ApiResponse[Output2]],
  )(implicit
    schema: Schema[Output2],
  ): Handler[R, E, Params, Input, Output2] =
    Handler
      .make[R, E, Params, Input, Output2](self.output[Output2]) { case (params, input) =>
        f(zipper.zip(params, input))
      }

  def toHttp[R, E, Output2](
    f: ZipOut => ZIO[R, E, Output2],
  )(implicit
    schema: Schema[Output2],
  ): HttpApp[R, E] =
    handle[R, E, Output2](f).toHttp

  def call(host: String)(params: ZipOut): ZIO[EventLoopGroup with ChannelFactory, Throwable, Unit] = {
    val tuple = zipper.unzip(params)
    ClientInterpreter.interpret(host)(self)(tuple._1, tuple._2).unit
  }
}
