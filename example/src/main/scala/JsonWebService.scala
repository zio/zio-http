import zhttp.http._
import zhttp.service.Server
import zio._

/**
 * Example to build app using JSON web service
 */
object JsonWebService extends App {

  case class Employee(id: String, name: String, Experience: String)
  val employees = List(Employee("1", "abc", "1yr"), Employee("2", "def", "2yrs"), Employee("3", "xyz", "3yrs"))

  //get employee details if employee exists
  def getDetails(id: String): String = {
    val emp = employees.filter(_.id.equals(id))
    if (emp.isEmpty) s"""Employee doesn't exist"""
    else s"""{"id": "${emp.head.id}", "name": "${emp.head.name}", "experience": "${emp.head.Experience}"}"""
  }

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect { case Method.GET -> Root / "get-employee-details" / id =>
    Response.jsonString(getDetails(id))
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
