package zio.http.endpoint.cli

import zio._
import zio.cli._

import zio.http.codec.HttpCodec._
import zio.http.codec.SimpleCodec.{Specified, Unspecified}
import zio.http.codec.{HttpCodec, HttpCodecType, SimpleCodec}
import zio.http.endpoint.{Endpoint, EndpointMiddleware}
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

  private def fromInput[Input](input: HttpCodec[HttpCodecType.RequestType, Input]): CliEndpoint[_] =
    input.asInstanceOf[HttpCodec[_, _]] match {
      case Combine(left, right, inputCombiner) => ???
      case Content(schema, index)              => ???
      case ContentStream(schema, index)        => ???
      case Empty                               => ???
      case Fallback(left, right)               => ???
      case Halt                                => ???
      case Header(name, textCodec, index)      => ???
      case Method(codec, index)                =>
        codec.asInstanceOf[SimpleCodec[_, _]] match {
          case SimpleCodec.Specified(method) =>
            CliEndpoint[Unit]((_, request) => request.copy(method = method.asInstanceOf[model.Method]), Options.none)
          case SimpleCodec.Unspecified()     =>
            CliEndpoint[Unit]((_, request) => request, Options.none)
        }
      case Path(textCodec, name, index)        => ???
      case Query(name, textCodec, index)       => ???
      case Status(codec, index)                => ???
      case TransformOrFail(api, f, g)          => ???
      case WithDoc(in, doc)                    => ???
    }
}

final case class CliRoutes[A](commands: Chunk[Command[A]])

/*
GET    /users           cli get     users 1
GET    /users/{id}      cli get     users 1
DELETE /users/{id}      cli delete  users 1
POST   /users           cli create  users --email test@test.com --name Jorge
PUT    /users/{id}      cli update  users 1

GET    /users?order=asc                   cli get     users --order asc
GET    /users/{group}/{id}?order=desc     cli get     users --group 1 --id 100 --order desc
 */

// final case class HeaderOutput()
// final case class ContentOutput()

// final case class EndpointOutput(headerOutputs: Chunk[HeaderOutput], contentOutput: ContentOutput)
