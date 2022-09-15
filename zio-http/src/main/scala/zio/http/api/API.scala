package zio.http.api

import zio.http.Method
import zio._

// Http[-R, +E, -A, +B] extends (A => ZIO[R, Option[E], B]) { self =>
// TODO: Index Atom
trait Schema[A]

final case class API[Input, Output](
  method: Method,
  in: In[Input],
//  outputCodec: OutputCodec[Input],
) { self =>
  def handle[R, E](f: Input => ZIO[R, E, Output]): HandledAPI[R, E, Input, Output] =
    HandledAPI(self, f)

  def in[Input2](in2: In[Input2])(implicit combiner: Combiner[Input, Input2]): API[combiner.Out, Output] =
    copy(in = self.in ++ in2)

  def output[Output2]: API[Input, Output2] =
    self.asInstanceOf[API[Input, Output2]]
}

object API {
  def delete[Input](route: In[Input]): API[Input, Unit] =
    API(Method.GET, route)

  def get[Input](route: In[Input]): API[Input, Unit] =
    API(Method.GET, route)

  def post[Input](route: In[Input]): API[Input, Unit] =
    API(Method.POST, route)

  def put[Input](route: In[Input]): API[Input, Unit] =
    API(Method.PUT, route)
}
