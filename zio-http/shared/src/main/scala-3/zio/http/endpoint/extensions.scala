package zio.http.endpoint

import zio.http.*
import zio.http.codec.*
import zio.schema.Schema
import zio.stream.ZStream

import scala.compiletime.*

extension [A](e: Either[A, A]) {

  inline def mergeOpt: A = e match {
    case Left(a) => a
    case Right(a) => a
  }
}

extension [AtomTypes, Value <: Res, Value2 <: Res, Res](self: HttpCodec[AtomTypes, Value]) {

  transparent inline def ||[AtomTypes1 <: AtomTypes](
      that:                HttpCodec[AtomTypes1, Value2]
    )(implicit alternator: Alternator[Value, Value2]
    ): HttpCodec[AtomTypes1, Res] =
    if self eq HttpCodec.Halt then that.asInstanceOf[HttpCodec[AtomTypes1, Res]]
    else if that eq HttpCodec.Halt then self.asInstanceOf[HttpCodec[AtomTypes1, Res]]
    else
      inline erasedValue[alternator.Out] match {
        case _: &[Value, Value2] =>
          HttpCodec
            .Fallback(self, that, alternator, HttpCodec.Fallback.Condition.IsHttpCodecError)
            .transform[Res](_.mergeOpt)(v => Left(v.asInstanceOf[Value]))
        case _: Value =>
          HttpCodec
            .Fallback(self, that, alternator, HttpCodec.Fallback.Condition.IsHttpCodecError)
            .transform[Res](_.mergeOpt)(v => Left(v.asInstanceOf[Value]))
        case _: Value2 =>
          HttpCodec
            .Fallback(self, that, alternator, HttpCodec.Fallback.Condition.IsHttpCodecError)
            .transform[Res](_.mergeOpt)(v => Right(v.asInstanceOf[Value2]))
        case _: Either[Value, Value2] =>
          HttpCodec
            .Fallback(self, that, alternator, HttpCodec.Fallback.Condition.IsHttpCodecError)
            .transform[Res](_.mergeOpt) {
              case v: Value => Left(v)
              case v: Value2 => Right(v)
          }
    }
}

extension [PathInput, Input, Err <: ErrorRes, Output <: Res, Auth <: AuthType, Res, ErrorRes](
    self: Endpoint[PathInput, Input, Err, Output, Auth]
  ) {

  transparent inline def orOut[Output2 <: Res : HttpContentCodec](
    implicit alt: Alternator[Output2, Output]
  ): Endpoint[PathInput, Input, Err, Res, Auth] =
    Endpoint(
      self.route,
      self.input,
      (HttpCodec.content[Output2] ++ StatusCodec.status(Status.Ok)) || self.output,
      self.error,
      self.codecError,
      self.documentation,
      self.authType,
      )

  transparent inline def orOut[Output2 <: Res : HttpContentCodec](
    doc: Doc
  )(
    implicit alt: Alternator[Output2, Output]
  ): Endpoint[PathInput, Input, Err, Res, Auth] =
    Endpoint(
      self.route,
      self.input,
      ((HttpCodec.content[Output2] ++ StatusCodec.status(Status.Ok)) ?? doc) || self.output,
      self.error,
      self.codecError,
      self.documentation,
      self.authType,
      )

  transparent inline def orOut[Output2 <: Res : HttpContentCodec](
    mediaType: MediaType
  )(
    implicit alt: Alternator[Output2, Output]
  ): Endpoint[PathInput, Input, Err, Res, Auth] =
    orOut[Output2](mediaType, Doc.empty)

  transparent inline def orOut[Output2 <: Res : HttpContentCodec](
    status: Status
  )(
    implicit alt: Alternator[Output2, Output]
  ): Endpoint[PathInput, Input, Err, Res, Auth] =
    orOut[Output2](status, Doc.empty)

  transparent inline def orOut[Output2 <: Res : HttpContentCodec](
    status: Status,
    doc   : Doc,
  )(
    implicit alt: Alternator[Output2, Output]
  ): Endpoint[PathInput, Input, Err, Res, Auth] =
    Endpoint(
      self.route,
      self.input,
      ((HttpCodec.content[Output2] ++ StatusCodec.status(status)) ?? doc) || self.output,
      self.error,
      self.codecError,
      self.documentation,
      self.authType,
      )

  transparent inline def orOut[Output2 <: Res : HttpContentCodec](
    mediaType: MediaType,
    doc      : Doc,
  )(
    implicit alt: Alternator[Output2, Output]
  ): Endpoint[PathInput, Input, Err, Res, Auth] =
    Endpoint(
      self.route,
      self.input,
      (HttpCodec.content[Output2](mediaType) ?? doc) || self.output,
      self.error,
      self.codecError,
      self.documentation,
      self.authType,
      )

  transparent inline def orOut[Output2 <: Res : HttpContentCodec](
    status   : Status,
    mediaType: MediaType,
    doc      : Doc,
  )(
    implicit alt: Alternator[Output2, Output]
  ): Endpoint[PathInput, Input, Err, Res, Auth] =
    Endpoint(
      self.route,
      self.input,
      ((HttpCodec.content[Output2](mediaType) ++ StatusCodec.status(status)) ?? doc) || self.output,
      self.error,
      self.codecError,
      self.documentation,
      self.authType,
      )

  transparent inline def orOut[Output2 <: Res : HttpContentCodec](
    status   : Status,
    mediaType: MediaType,
  )(
    implicit alt: Alternator[Output2, Output]
  ): Endpoint[PathInput, Input, Err, Res, Auth] =
    orOut[Output2](status, mediaType, Doc.empty)

  transparent inline def orOutCodec[Output2 <: Res](
    codec: HttpCodec[HttpCodecType.ResponseType, Output2]
  )(
    implicit alt: Alternator[Output2, Output]
  ): Endpoint[PathInput, Input, Err, Res, Auth] =
    Endpoint(
      self.route,
      self.input,
      codec || self.output,
      self.error,
      self.codecError,
      self.documentation,
      self.authType,
      )

  transparent inline def orOutError[Err2 <: ErrorRes : HttpContentCodec](
    status: Status
  )(
    implicit alt: Alternator[Err2, Err]
  ): Endpoint[PathInput, Input, ErrorRes, Output, Auth] =
    self.copy[PathInput, Input, ErrorRes, Output, Auth](
      error =
        (ContentCodec.content[Err2]("error-response") ++ StatusCodec.status(status)) || self.error
      )

  transparent inline def orOutError[Err2 <: ErrorRes : HttpContentCodec](
    status: Status,
    doc   : Doc,
  )(
    implicit alt: Alternator[Err2, Err]
  ): Endpoint[PathInput, Input, ErrorRes, Output, Auth] =
    self.copy[PathInput, Input, ErrorRes, Output, Auth](
      error = ((ContentCodec.content[Err2]("error-response") ++ StatusCodec.status(
        status
        )) ?? doc) || self.error
      )

}
