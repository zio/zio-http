package zhttp.http

final case class QueryParameters(raw: Map[String, List[String]]) { self =>
  def ++(other: QueryParameters): QueryParameters = self.combine(other)

  def combine(other: QueryParameters): QueryParameters = QueryParameters(self.raw ++ other.raw)

  def queryParams: QueryParameters = self
}

object QueryParameters {

  val empty: QueryParameters = QueryParameters(Map.empty[String, List[String]])

}
