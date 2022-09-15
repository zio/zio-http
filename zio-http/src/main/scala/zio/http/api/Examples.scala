// package zio.http.api

// import zio.Chunk

// object Example extends App {

//   import In._

//   val sample: In[(Int, Int, Int)] =
//     literal("users") ++ int ++ literal("posts") ++ int ++ literal("comments") ++ int
//   val result                      = Chunk((), 100, (), 500, (), 900)
//   val constructor                 = thread(sample)
//   val threaded                    = constructor(result)

//   println(threaded)
// }

// object Example2 extends App {

//   import zio.Chunk
//   import In._

//   val sample: In[(Int, Int)] =
//     literal("users") ++ int ++ literal("posts") ++ int

//   val result = Chunk((), 500, (), 900)

//   val constructor = thread(sample)
//   val threaded    = constructor(result)

//   println(threaded)
// }

// object Example3 extends App {

//   import zio.Chunk
//   import In._

//   val sample = (literal("users") ++ int.map(n => ("COOL", n * 2)) ++ literal("posts") ++ int).map(n => ("Adam", n))

//   val result = Chunk((), 500, (), 900)

//   val constructor = thread(sample)
//   val threaded    = constructor(result)

//   println(threaded)
// }

// object Example4 extends App {

//   import zio.Chunk
//   import In._

//   val sample = literal("users") / int / literal("posts") / int // / In.Header(HeaderParser.header("AUTH"))

//   val result = Chunk((), 500, (), 900, "some-auth-token")

//   val constructor = thread(sample)
//   val threaded    = constructor(result)

//   println(threaded)
// }

// // object Example4 extends App {

// //   import zio.Chunk
// //   import In._

// //   val x = literal("users") / int / literal("posts") / int // / In.Header(HeaderParser.header("origin"))

// //   import In._

// // API, In
// // QueryParser, HeaderParser, In, API, ....

// // val tapir =
// //   endpoint
// //     .in("user" / param[Int] / "posts" / param[UUID])
// //     .in(query[String]("hello") ++ query[String]("hello"))
// //     .in(header[String]("hello") ++ header[String]("hello"))

// // val oauthCode: Header[Code] = header[String]("Authorization").transform(Code(_), _.value)
// // def oauth(name: String) = header(name).transform(_ => ???, _ => ???)
// // val oauth = header("path").transform(_ => ???, _ => ???)

// // val sample = API
// //   .post("user" / int / "posts" / uuid)
// //   .in(header("lifespan") zip header("name") zip query("hello"))
// //   .In

// // val sample2 =
// //   route("user") ++ int ++ route("posts") ++ header("lifespan") ++ header("name") ++ query("hello") ++ uuid ++ get

// // val sample = API
// //   .get("user" / int / "posts" / uuid)
// //   .in(
// //     header("lifespan").int ++ header("name") ++ query("hello").asUUID
// //   )
// //   .In

// // val flattened: Chunk[Atom[_]] = flatten(sample)
// // println(flattened)

// // val result = Chunk((), 500, (), 900, "some-auth-token")

// // val constructor = thread(sample)
// // val threaded    = constructor(result)

// // println(threaded)
// // }
