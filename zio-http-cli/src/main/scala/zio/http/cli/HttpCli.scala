package zio.http.endpoint.cli

import zio._
import zio.cli._
import zio.http.endpoint.internal.TextCodec
import zio.http.endpoint.{CodecType, Endpoint, EndpointMiddleware, HttpCodec}
import zio.http.{Middleware, Request, Response, model}

final case class CliEndpoint[A](embed: (A, Request) => Request, options: Options[A]) { self =>
  def ++[B](that: CliEndpoint[B]): CliEndpoint[(A, B)] =
    CliEndpoint(
      { case ((a, b), request) =>
        that.embed(b, self.embed(a, request))
      },
      self.options ++ that.options,
    )
}
object CliEndpoint                                                                   {
  def fromEndpoint[In, Err, Out, M <: EndpointMiddleware](endpoint: Endpoint[In, Err, Out, M]): CliEndpoint[_] =
    fromInput(endpoint.input)

  private def fromInput[Input](input: HttpCodec[CodecType.RequestType, Input]): CliEndpoint[_] =
    input.asInstanceOf[HttpCodec[_, _]] match {
      case HttpCodec.Halt                                => ???
      case HttpCodec.TransformOrFail(api, f, g)          => ???
      case HttpCodec.Combine(left, right, inputCombiner) => ???
      case HttpCodec.WithDoc(in, doc)                    => ???
      case HttpCodec.Query(name, textCodec, index)       => ???
      case HttpCodec.Status(textCodec, index)            => ???
      case HttpCodec.Method(methodCodec, index)          =>
        methodCodec.asInstanceOf[TextCodec[_]] match {
          case TextCodec.StringCodec      => ???
          case TextCodec.BooleanCodec     => ???
          case TextCodec.IntCodec         => ???
          case TextCodec.UUIDCodec        => ???
          case TextCodec.Constant(string) =>
            val method = model.Method.fromString(string)
            CliEndpoint[Unit]((_, request) => request.copy(method = method), Options.none)
        }
      case HttpCodec.Header(name, textCodec, index)      => ???
      case HttpCodec.Body(schema, index)                 => ???
      case HttpCodec.Path(textCodec, name, index)        => ???
      case HttpCodec.BodyStream(schema, index)           => ???
      case HttpCodec.Fallback(left, right)               => ???
      case HttpCodec.Empty                               => ???
    }
}

final case class CliRoutes[A](commands: Chunk[Command[A]])

/*
GET    /users           cli get     users 1
GET    /users/{id}      cli get     users 1
DELETE /users/{id}      cli delete  users 1
POST   /users           cli create  users 1
PUT    /users/{id}      cli update  users 1
 */

/*
GET    /users?order=asc                   cli get     users --order asc
GET    /users/{group}/{id}?order=desc     cli get     users --group 1 --id 100 --order desc
DELETE /users/{group}/{id}                cli delete  users id
POST   /users                             cli create  users --email test@test.com --name Jorge
PUT    /users/{group}/{id}                cli update  users id
 */

// final case class HeaderOutput()
// final case class ContentOutput()

// final case class EndpointOutput(headerOutputs: Chunk[HeaderOutput], contentOutput: ContentOutput)
