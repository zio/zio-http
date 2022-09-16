package zio.http.api.internal

import zio._
import zio.http.api._
import zio.http.{Request, Response}

final case class HandlerMatch[-R, +E, Input, Output](
  handledApi: HandledAPI[R, E, Input, Output],
  // route parsing results
  results: Chunk[Any],
) {

  val in       = handledApi.api.input
  val atoms    = In.flatten(in)
  val threader = In.thread(in)

  // TODO: precompute threader and anything else that's derivable from API itself
  def run(request: Request): ZIO[R, E, Response] = {
    // All Results
    val queryResults: Chunk[Any] = parseQuery(request, atoms.queries)

    // Reassembled and Threaded
    val fullResults = In.InputResults(results, queryResults)
    val _           = fullResults
    val input       = threader(fullResults)
    handledApi.handler(input).map { out =>
      Response.text(out.toString)
    }
  }

  def parseQuery(request: Request, queryAtoms: Chunk[In.Query[_]]): Chunk[Any] = {
    queryAtoms.map { queryAtom =>
      request.url.queryParams(queryAtom.name).head
    }
  }
}

// API.get( uuid / "foo" / query("foo") / string / "bar" / int / "baz" / query("bar"))
// (UUID, String, String, Int, String)
