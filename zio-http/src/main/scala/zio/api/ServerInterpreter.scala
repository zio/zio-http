package zhttp.api

import zhttp.http.{Http, HttpApp, Request, Response}
import zio.schema.Schema
import zio.schema.codec.JsonCodec
import zio.{Chunk, ZIO}

private[api] object ServerInterpreter {

  def handlerToHttpApp[R, E, Params, Input, Output](
    handler: Handler[R, E, Params, Input, Output],
  ): HttpApp[R, E] = {
    val requestCodec: RequestCodec[Params]   = handler.api.requestCodec
    val outputEncoder: Output => Chunk[Byte] = JsonCodec.encode(handler.api.outputSchema)

    // TODO:
    //  Use request.unsafeEncode
    //  First, parse the url, then parse the remaining query string if there is one
    def withInput(request: Request)(process: Input => ZIO[R, E, Response]): ZIO[R, E, Response] =
      handler.api.inputType match {
        case InputType.ZIOInput(schema) =>
          if (schema == Schema[Unit])
            process(().asInstanceOf[Input])
          else
            request.body.asChunk.flatMap { bytes =>
              val inputDecoder: Chunk[Byte] => Either[String, Input] = JsonCodec.decode(schema)
              inputDecoder(bytes) match {
                case Left(err)    => ZIO.succeed(Response.text(s"Invalid input: $err"))
                case Right(input) => process(input)
              }
            }.catchAll { err =>
              ZIO.succeed(Response.text(s"Error parsing request body: $err"))
            }

        case InputType.StreamInput =>
          process(request.body.asStream.asInstanceOf[Input])
      }

    //
    // Problem, combined input/params would require the parseRequest to be effectful
    // RequestCodec[A]
    // RequestCodec.Zip(Path("users"), ParseBody[User])
    // parseRequest[A](request: Request, requestCoder: RequestCodec[A]): Option[A]
    // parseRequest[A](request: Request, requestCoder: RequestCodec[A]): ZIO[Option[A]]
    // options:
    // 1. keep separate
    // 2. make parseRequest effectful
    // 3. return Either[A, Task[A]] (an approach used in async)

    // API.get("users" / id).input[User]

    // Dynamo DB
    // =========
    // Query[A] composed of many zips
    // 1. the tree has to be picked apart into separate branches
    // 2. feed the parallel computed results and zip them together
    // A bunch of GETs can be converted to a batched GET query
    //
    // API[(A, B, C, D, E)]
    // RouteParser[(A, C, E)]
    // HeaderParser[(B, D)]
    //
    // Type Erasure and Index Tracking
    // Flattened into primitives
    // Chunk[Any] maybe Zipper could push and pop from Array

    // 1. Unify Route and Params and InputBody into a single Input type
    // 2. Fix semantics of request parsing.
    //    - If the ROUTE is invalid, try to match the next route
    //    - If the ROUTE matches but query params or headers do not match, return an error
    // 3. Improve performance by `orElse`-ing the Routes (prefix tree)
    // 4. Collect and Rebuild parsers (tapir technique)
    //    - Parse the route "aoseuth/aoeusnth/aoeusth" // "/users/:id" zip headerParser zip "posts/:id" zip input
    //                                                              (UUID, HeaderType, PostId, InputType)
    //                                                              (0   , 1         , 2     , 3)
    //    - Parse the headers
    //    - Parse the query params
    //    - Read and parse the input body (effectful) // (BodyType, 4)
    //    - requestCodec.getRouteParser // "users/:id/posts" ((UUID, 0), (PostId, 2))
    //    - requestCodec.getHeadersParser // headerParser (HeaderType)
    Http.collectZIO {
      case req @ requestCodec(result) if req.method == handler.api.method =>
        withInput(req) { input =>
          handler
            .handle((result, input))
            .map { apiResponse =>
              if (handler.api.outputSchema == Schema[Unit])
                Response.ok
              else
                Response
                  .json(new String(outputEncoder(apiResponse.value).toArray))
                  .addHeaders(apiResponse.headers)
                  .setStatus(apiResponse.status)
            }
        }
    }
  }

}
