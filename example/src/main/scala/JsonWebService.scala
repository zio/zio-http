import zhttp.http._
import zhttp.service.Server
import zio._
import io.circe.Encoder
import io.circe.syntax.EncoderOps

/**
 * Example to build app using JSON web service
 */
object JsonWebService extends App {

  final case class Employee(id: Long, name: String, experience: Int)

  implicit val employeeEncoder: Encoder[Employee] =
    Encoder.forProduct3("id", "name", "experience")(emp => (emp.id, emp.name, emp.experience))

  val employees = List(Employee(1, "abc", 3), Employee(2, "def", 2), Employee(3, "xyz", 4))

  //get employee details if employee exists
  def getDetails(id: String): String = {
    val emp = employees.filter(_.id.toString.equals(id))
    if (emp.isEmpty) s"""Employee doesn't exist"""
    else emp.head.asJson.noSpaces
  }

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect { case Method.GET -> Root / "get-employee-details" / id =>
    Response.jsonString(getDetails(id))
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
