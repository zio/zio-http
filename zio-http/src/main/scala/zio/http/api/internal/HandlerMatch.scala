package zio.http.api.internal

import zio._
import zio.http.api._
import zio.http.Request

final case class HandlerMatch[-R, +E, Input, Output](
  handledApi: HandledAPI[R, E, Input, Output],
  // route parsing results
  results: Chunk[Any],
) {
  // TODO: precompute threader and anything else that's derivable from API itself
  def run(request: Request): ZIO[R, E, Output] = {
    val threader = In.thread(handledApi.api.in)
    // add a map (index -> actual)

    val queryAtoms = In.flatten(handledApi.api.in).collect { case queryAtom @ In.Query(_, _) => queryAtom }
    val queryResults: Chunk[Any] = parseQuery(request, queryAtoms)

    val fullResults = results ++ queryResults // ++ headerResults ++ bodyResults
    // val fullResults: Chunk[Any] = combiner(results, queryResults)

    // 1. expects result chunk to be in the zippable (atom <-> result for atom)
    val input = threader(fullResults)
    handledApi.handler(input)
  }

  def parseQuery(request: Request, queryAtoms: Chunk[In.Query[_]]): Chunk[Any] = {
    queryAtoms.map { queryAtom =>
      request.url.queryParams(queryAtom.name).head
    }
  }
}

// API.get( uuid / "foo" / query("foo") / string / "bar" / int / "baz" / query("bar"))
// (UUID, String, String, Int, String)
