package zhttp.internal

import zhttp.http.Status
import zio.test.AssertionM.Render.param
import zio.test._

trait IntegrationTestAssertions {
  def status(status: Status): Assertion[Status] =
    Assertion.assertion("status")(param(status))(_ == status)
}
