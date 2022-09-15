// package zio.http.api.internal

// import zio._
// import zio.http.api._

// final case class ZippableHandledAPI[-R, +E, Out](
//   routeAtoms: Chunk[In.Route[_]],
//   headerAtoms: Chunk[In.Header[_]],
//   queryAtoms: Chunk[In.Query[_]],
//   inputBodyAtom: Option[In.InputBody[_]],
//   handler: Chunk[Any] => ZIO[R, E, Out],
// )

// object ZippableHandledAPI {
//   def fromHandledAPI[R, E, Out](api: HandledAPI[R, E, _, Out]): ZippableHandledAPI[R, E, Out] = {
//     val flattened                  = In.flatten(api.api.in)
//     val routeAtoms                 = flattened.collect { case atom: In.Route[_] => atom }
//     val headerAtoms                = flattened.collect { case atom: In.Header[_] => atom }
//     val queryAtoms                 = flattened.collect { case atom: In.Query[_] => atom }
//     val inputBodyAtom              = flattened.collectFirst { case atom: In.InputBody[_] => atom }
//     def handler(chunk: Chunk[Any]) = ZIO.debug(s"Handling $chunk with API $api").asInstanceOf[ZIO[R, E, Out]]
//     ZippableHandledAPI(routeAtoms, headerAtoms, queryAtoms, inputBodyAtom, handler)
//   }

// }
