package zio.http.h2.hpack

object StaticTable {
  private val Entries: Array[HeaderField] = Array(
    HeaderField(":authority", ""),
    HeaderField(":method", "GET"),
    HeaderField(":method", "POST"),
    HeaderField(":path", "/"),
    HeaderField(":path", "/index.html"),
    HeaderField(":scheme", "http"),
    HeaderField(":scheme", "https"),
    HeaderField(":status", "200"),
    HeaderField(":status", "204"),
    HeaderField(":status", "206"),
    HeaderField(":status", "304"),
    HeaderField(":status", "400"),
    HeaderField(":status", "404"),
    HeaderField(":status", "500"),
    HeaderField("accept-charset", ""),
    HeaderField("accept-encoding", "gzip, deflate"),
    HeaderField("accept-language", ""),
    HeaderField("accept-ranges", ""),
    HeaderField("accept", ""),
    HeaderField("access-control-allow-origin", ""),
    HeaderField("age", ""),
    HeaderField("allow", ""),
    HeaderField("authorization", ""),
    HeaderField("cache-control", ""),
    HeaderField("content-disposition", ""),
    HeaderField("content-encoding", ""),
    HeaderField("content-language", ""),
    HeaderField("content-length", ""),
    HeaderField("content-location", ""),
    HeaderField("content-range", ""),
    HeaderField("content-type", ""),
    HeaderField("cookie", ""),
    HeaderField("date", ""),
    HeaderField("etag", ""),
    HeaderField("expect", ""),
    HeaderField("expires", ""),
    HeaderField("from", ""),
    HeaderField("host", ""),
    HeaderField("if-match", ""),
    HeaderField("if-modified-since", ""),
    HeaderField("if-none-match", ""),
    HeaderField("if-range", ""),
    HeaderField("if-unmodified-since", ""),
    HeaderField("last-modified", ""),
    HeaderField("link", ""),
    HeaderField("location", ""),
    HeaderField("max-forwards", ""),
    HeaderField("proxy-authenticate", ""),
    HeaderField("proxy-authorization", ""),
    HeaderField("range", ""),
    HeaderField("referer", ""),
    HeaderField("refresh", ""),
    HeaderField("retry-after", ""),
    HeaderField("server", ""),
    HeaderField("set-cookie", ""),
    HeaderField("strict-transport-security", ""),
    HeaderField("transfer-encoding", ""),
    HeaderField("user-agent", ""),
    HeaderField("vary", ""),
    HeaderField("via", ""),
    HeaderField("www-authenticate", "")
  )

  val length: Int = Entries.length

  def get(index: Int): Option[HeaderField] = {
    val zeroBased = index - 1
    if (zeroBased >= 0 && zeroBased < Entries.length) Some(Entries(zeroBased)) else None
  }

  def indexOf(field: HeaderField): Int = {
    val normalizedName = DynamicTable.normalizeName(field.name)
    var i              = 0
    while (i < Entries.length) {
      val candidate = Entries(i)
      if (candidate.name == normalizedName && candidate.value == field.value) return i + 1
      i += 1
    }
    -1
  }

  def indexOfName(name: String): Int = {
    val normalizedName = DynamicTable.normalizeName(name)
    var i              = 0
    while (i < Entries.length) {
      if (Entries(i).name == normalizedName) return i + 1
      i += 1
    }
    -1
  }
}
