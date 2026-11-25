package zio.http.gen.model

case class Data(name: String, meta: Meta)
object Data {
  implicit val codec: zio.schema.Schema[Data] = zio.schema.DeriveSchema.gen[Data]
}
