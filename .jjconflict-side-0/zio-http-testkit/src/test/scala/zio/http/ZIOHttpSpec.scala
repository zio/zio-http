package zio.http

import zio._
import zio.test._

trait ZIOHttpSpec extends ZIOSpecDefault {
  override def aspects: Chunk[TestAspectPoly] =
    Chunk(TestAspect.timeout(60.seconds), TestAspect.timed)
}
