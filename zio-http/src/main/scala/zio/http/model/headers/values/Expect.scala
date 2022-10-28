package zio.http.model.headers.values

/**
 * The Expect HTTP request header indicates expectations that need to be met by
 * the server to handle the request successfully. There is only one defined
 * expectation: 100-continue
 */
sealed trait Expect {
  val value: String
}

object Expect {
  case object ExpectValue extends Expect {
    override val value: String = "100-continue"
  }

  case object InvalidExpectValue extends Expect {
    override val value: String = ""
  }

  def fromExpect(expect: Expect): String =
    expect.value

  def toExpect(value: String): Expect =
    value match {
      case ExpectValue.value => ExpectValue
      case _                 => InvalidExpectValue
    }
}
