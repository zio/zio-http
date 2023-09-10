// EndpointDocs.scala

object EndpointDocs {
  implicit class EndpointDocs(val sc: StringContext) extends AnyVal {
    def doc(args: Any*): String = {
      // Access the parts of the interpolated string
      val parts = sc.parts.iterator

      // Combine the parts with the arguments to create the final documentation string
      val result = new StringBuilder(parts.next())
      while (parts.hasNext) {
        val arg = args(parts.nextIndex - 1).toString
        result.append(arg).append(parts.next())
      }

      // Return the documentation string
      result.toString()
    }
  }
}
