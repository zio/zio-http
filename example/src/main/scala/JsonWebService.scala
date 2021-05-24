import zhttp.http._
import zhttp.service.Server
import zio.json._
import zio._

/**
 * Example to build app using JSON web service
 */
object JsonWebService extends App {

  case class Employee(id: String, name: String, experience: String)
  object Employee {
    implicit val encoder: JsonEncoder[Employee] = DeriveJsonEncoder.gen[Employee]
  }

  val employees = List(Employee("1", "abc", "1yr"), Employee("2", "def", "2yrs"), Employee("3", "xyz", "3yrs"))

  //get employee details if employee exists
  def getDetails(id: String): String = {
    val emp = employees.filter(_.id.equals(id))
    if (emp.isEmpty) s"""Employee doesn't exist"""
    else emp.head.toJson
  }

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect { case Method.GET -> Root / "get-employee-details" / id =>
    Response.jsonString(getDetails(id))
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
