---
id: flash
title: Flash Messages
sidebar_label: Flash
---

Flash messages are temporary short-lived messages that are displayed to users on web applications to inform users about the outcome of certain actions, such as form submissions. These messages are typically stored in session data but are automatically removed after being displayed to the user.

Flash messages are particularly useful in scenarios involving HTTP redirections, where there may not be a dedicated view to display messages directly. They help maintain a smooth user experience by providing timely feedback without cluttering the interface.

## Motivation

Assume we have a simple web application that allows users to submit a form. After the form is submitted, we want to display a success message to the user. We also want to display an error message if the form submission fails. How can we implement this?

Assume we have a simple form that submits a user's name and age to the `/users/save` endpoint:

```html
<form id="myform" action="/users/save" method="POST">
  <label for="name">Name:</label><br/>
  <input type="text" id="name" name="name"/><br/>
  <label for="age">Age:</label><br/>
  <input type="number" id="age" name="age"/><br/><br/>
  <button type="submit">Submit</button>
</form>
```

After the form is submitted, we want to display the outcome of the form submission to the user. We want to display a success message if the form submission is successful, and an error message if the form submission fails. Additionally, we want to display the list of users we have submitted so far.

One simple solution is to respond with an HTML page that contains the messages and the list of users on the same endpoint (`/users/save`). However, this approach has a drawback: if the user refreshes the page, the form will be resubmitted, which may lead to duplicate submissions:

```scala mdoc:invisible
import zio.http.template._

case class User(name: String, age: Int)

object ui {

  def renderNotice(html: Html): Html = div(styleAttr := "background: green", html)

  def renderAlert(html: Html): Html = div(styleAttr := "background: red", html)

  def renderUsers(users: List[User]): Html = {
    table(
      borderAttr := "1",
      tHead(
        tr(
          th("Name"),
          th("Age"),
        ),
      ),
      tBody(
        ol(users.map { u =>
          tr(
            td(u.name),
            td(u.age.toString),
          )
        }),
      ),
    )
  }

}
```

```scala mdoc:compile-only
import zio._
import zio.http._

val saveUserRoute: Route[Ref[List[User]], Nothing] =
  Method.POST / "users" / "save" -> handler { (req: Request) =>
    for {
      usersDb <- ZIO.service[Ref[List[User]]]
      form    <- req.body.asURLEncodedForm.orDie
      name    <- ZIO.fromOption(form.get("name")).flatMap(_.asText)
      age     <- ZIO.fromOption(form.get("age")).flatMap(_.asText).map(_.toInt)
      users   <- usersDb.updateAndGet(_ appended User(name, age))
    } yield Response.html(ui.renderNotice("User saved successfully!") ++ ui.renderUsers(users))
  }.catchAll { _ =>
    handler {
      Response.html(
        data = ui.renderAlert("Failed to save user! Something went wrong!"),
        status = Status.Forbidden
      )
    }
  }
```

<details>
<summary><b>Full Implementation Showcase</b></summary>

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.codec.PathCodec
import zio.http.template._

case class User(name: String, age: Int)

object ui {

  def renderNotice(html: Html): Html = div(styleAttr := "background: green", html)

  def renderAlert(html: Html): Html = div(styleAttr := "background: red", html)

  def renderUsers(users: List[User]): Html = {
    table(
      borderAttr := "1",
      tHead(
        tr(
          th("Name"),
          th("Age"),
        ),
      ),
      tBody(
        ol(users.map { u =>
          tr(
            td(u.name),
            td(u.age.toString),
          )
        }),
      ),
    )
  }

}

object NotificationWithoutFlash extends ZIOAppDefault {
  val homeRoute: Route[Any, Nothing] =
    Method.GET / PathCodec.empty -> handler {
      Response.html(
        form(idAttr := "myform", actionAttr := "/users/save", methodAttr := "POST",
          label("Name:", forAttr("name")), br(),
          input(typeAttr := "text", idAttr := "name", nameAttr := "name"), br(),
          label("Age:", forAttr := "age"), br(),
          input(typeAttr := "number", idAttr := "age", nameAttr := "age"), br(), br(),
          button("Submit", typeAttr := "submit"),
        ),
      )
    }

  val saveUserRoute: Route[Ref[List[User]], Nothing] =
    Method.POST / "users" / "save" -> handler { (req: Request) =>
      for {
        usersDb <- ZIO.service[Ref[List[User]]]
        form    <- req.body.asURLEncodedForm.orDie
        name    <- ZIO.fromOption(form.get("name")).flatMap(_.asText)
        age     <- ZIO.fromOption(form.get("age")).flatMap(_.asText).map(_.toInt)
        users   <- usersDb.updateAndGet(_ appended User(name, age))
      } yield Response.html(ui.renderNotice("User saved successfully!") ++ ui.renderUsers(users))
    }.catchAll { _ =>
      handler {
        Response.html(
          data = ui.renderAlert("Failed to save user! Something went wrong!"),
          status = Status.Forbidden
        )
      }
    }


  def run = Server.serve(Routes(saveUserRoute, homeRoute))
    .provide(Server.default, ZLayer(Ref.make(List.empty[User])))
}
```

Run the server and open the browser to `http://localhost:8080`. You will see a form to submit user details. After submitting the form, you will see the outcome of the form submission on the `/users/save` endpoint. Now refresh the page and you will see the form is resubmitted.

</details>

So it is better to redirect the user to a different endpoint after the form submission. In this case, we may want to redirect the user to the `/users` endpoint after the form submission. However, we need to find a way to pass the notification messages `/users` endpoint.

## Solution

The flash messages are designed to solve this problem. In such a scenario, the form endpoint (`/users/save`) will not be responsible for displaying the messages. Instead, it will be responsible for adding the new user to the database, storing the outcome of the form submission in the session data, and finally redirecting the user to the `/users` endpoint.

The `/users` endpoint will then read the message from the session data and display it to the user. The message will be removed from the session data after being displayed to the user.

```scala mdoc:invisible:nest
object ui {
  def renderNoFlash = Html.fromString("no-flash")

  def renderBothMessage(notice: Html, alert: Html): Html = notice ++ alert

  def renderNotice(html: Html): Html = div(styleAttr := "background: green", html)

  def renderAlert(html: Html): Html = div(styleAttr := "background: red", html)

  def renderUsers(users: List[User]): Html = {
    table(
      borderAttr := "1",
      tHead(
        tr(
          th("Name"),
          th("Age"),
        ),
      ),
      tBody(
        ol(users.map { u =>
          tr(
            td(u.name),
            td(u.age.toString),
          )
        }),
      ),
    )
  }

}
```

```scala mdoc:compile-only
import zio._
import zio.http._

val saveUserRoute: Route[Flash.Backend with Ref[List[User]], Nothing] =
  Method.POST / "users" / "save" -> handler { (req: Request) =>
    for {
      usersDb <- ZIO.service[Ref[List[User]]]
      flashBackend <- ZIO.service[Flash.Backend]
      form    <- req.body.asURLEncodedForm
      name    <- ZIO.fromOption(form.get("name")).flatMap(_.asText)
      age     <- ZIO.fromOption(form.get("age")).flatMap(_.asText).map(_.toInt)
      _       <- usersDb.update(_ appended User(name, age))
      response <- flashBackend.addFlash(
          response = Response.seeOther(URL.root / "users"),
          setter = Flash.setNotice("User saved successfully!")
        )
    } yield response
  }.catchAll { _ =>
    handler {
      for {
        flashBackend <- ZIO.service[Flash.Backend]
        response <- flashBackend.addFlash(
            response = Response.seeOther(URL.root / "users"),
            setter = Flash.setAlert("Failed to save user! Something went wrong!")
          )
      } yield response
    }
  }

val getUsersRoute: Route[Ref[List[User]] with Flash.Backend, Nothing] =
  Method.GET / "users" -> handler { (req: Request) =>
    for {
      flashBackend <- ZIO.service[Flash.Backend]
      usersDb      <- ZIO.service[Ref[List[User]]]
      users        <- usersDb.get
      usersHTML = ui.renderUsers(users)
      html <- flashBackend.flashOrElse(
        request = req,
        flash = Flash.getMessageHtml.foldHtml(ui.renderNotice, ui.renderAlert)(ui.renderBothMessage),
      )(ui.renderNoFlash)
    } yield Response.html(html ++ usersHTML)
  }
```

<details>
<summary><b>Full Implementation Showcase</b></summary>

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.codec.PathCodec

case class User(name: String, age: Int)

import zio.http.template._

object ui {
  def renderNoFlash = Html.fromString("no-flash")

  def renderBothMessage(notice: Html, alert: Html): Html = notice ++ alert

  def renderNotice(html: Html): Html = div(styleAttr := "background: green", html)

  def renderAlert(html: Html): Html = div(styleAttr := "background: red", html)

  def renderUsers(users: List[User]): Html = {
    table(
      borderAttr := "1",
      tHead(
        tr(
          th("Name"),
          th("Age"),
        ),
      ),
      tBody(
        ol(users.map { u =>
          tr(
            td(u.name),
            td(u.age.toString),
          )
        }),
      ),
    )
  }

}

object NotificationWithFlash extends ZIOAppDefault {
  val homeRoute: Route[Any, Nothing] =
    Method.GET / PathCodec.empty -> handler {
      Response.html(
        form(idAttr := "myform", actionAttr := "/users/save", methodAttr := "POST",
          label("Name:", forAttr("name")), br(),
          input(typeAttr := "text", idAttr := "name", nameAttr := "name"), br(),
          label("Age:", forAttr := "age"), br(),
          input(typeAttr := "number", idAttr := "age", nameAttr := "age"), br(), br(),
          button("Submit", typeAttr := "submit"),
        ),
      )
    }
    
val saveUserRoute: Route[Flash.Backend with Ref[List[User]], Nothing] =
  Method.POST / "users" / "save" -> handler { (req: Request) =>
    for {
      usersDb <- ZIO.service[Ref[List[User]]]
      flashBackend <- ZIO.service[Flash.Backend]
      form    <- req.body.asURLEncodedForm
      name    <- ZIO.fromOption(form.get("name")).flatMap(_.asText)
      age     <- ZIO.fromOption(form.get("age")).flatMap(_.asText).map(_.toInt)
      _       <- usersDb.update(_ appended User(name, age))
      response <- flashBackend.addFlash(
          response = Response.seeOther(URL.root / "users"), 
          setter = Flash.setNotice("User saved successfully!")
        )
    } yield response
  }.catchAll { _ =>
    handler {
      for {
        flashBackend <- ZIO.service[Flash.Backend]
        response <- flashBackend.addFlash(
            response = Response.seeOther(URL.root / "users"),
            setter = Flash.setAlert("Failed to save user! Something went wrong!")
          )
      } yield response
    }
  }


val getUsersRoute: Route[Ref[List[User]] with Flash.Backend, Nothing] =
  Method.GET / "users" -> handler { (req: Request) =>
    for {
      flashBackend <- ZIO.service[Flash.Backend]
      usersDb      <- ZIO.service[Ref[List[User]]]
      users        <- usersDb.get
      usersHTML = ui.renderUsers(users)
      html <- flashBackend.flashOrElse(
        request = req,
        flash = Flash.getMessageHtml.foldHtml(ui.renderNotice, ui.renderAlert)(ui.renderBothMessage),
      )(ui.renderNoFlash)
    } yield Response.html(html ++ usersHTML)
  }

  val app = Routes(saveUserRoute, getUsersRoute, homeRoute)

  def run = Server.serve(app).provide(Server.default, Flash.Backend.inMemory, ZLayer(Ref.make(List.empty[User])))
}
```

If we run the server and open the browser to `http://localhost:8080`, we will see a form to submit user details. After submitting the form, we will see the outcome of the form submission on the `/users` endpoint. The `/users` endpoint is responsible for getting the stored flash messages and displaying them to the user.

If we refresh the page, the form won't be resubmitted, and the notification won't be displayed again. Flash messages are one-time messages that disappear after the first read.

</details>

## Setting/Retrieving Flash Messages

There are two types of flash messages, notice and alert. To set a flash message, we use the `Flash.setNotice` and `Flash.setAlert` methods. These methods take a message of type `A` and return a `Flash.Setter[A]` object:

```scala mdoc:compile-only
import zio.http._
import zio.schema.DeriveSchema

case class CustomAlert(
  message: String,
  cause: String
)

implicit val customAlertSchema = DeriveSchema.gen[CustomAlert]

val notice1: Flash.Setter[String] = 
  Flash.setNotice("The form was submitted successfully!")
val notice2: Flash.Setter[Int] = 
  Flash.setNotice(10)

val alert1: Flash.Setter[String] = 
  Flash.setAlert("The form submission failed. Please try again!")
val alert2: Flash.Setter[CustomAlert] = 
  Flash.setAlert(CustomAlert("The form submission failed!", "Invalid form data."))
```

The `Flash.Setter[A]` object is used to set the flash message in the session data by calling the `Response#addFlash` or `Flash.Backend#addFlash` methods:

```scala mdoc:invisible
import zio.http._
val response = Response.ok
val notice1 = Flash.setNotice("The form was submitted successfully!")
val alert1 = Flash.setAlert("The form submission failed. Please try again!")
```

```scala mdoc:compile-only
val response1: Response = response.addFlash(notice1)
val response2: Response = response.addFlash(alert1)
```

The notice and alert flash messages are stored in the session data with their respective predefined keys, `notice` and `alert`. We have also a general setter to set a flash message with a custom key, `Flash.setValue`. It takes a key and a value of type `A` and returns a `Flash.Setter[A]` object:

```scala mdoc:compile-only
import zio.http._
import zio.schema.DeriveSchema

val setter1: Flash.Setter[String] = 
  Flash.setValue("security", "You haven't changed your password for a long time!")
val setter2: Flash.Setter[Int] = 
  Flash.setValue("quota", 90)

case class Notification(
  message: String,
  service: String,
  severity: Int 
)
implicit val notificationSchema = DeriveSchema.gen[Notification]
val setter3: Flash.Setter[Notification] = 
  Flash.setValue("notification", Notification("Service is down!", "Database", 3))
```

The flash messages can be retrieved from the session data by calling the `Request.flash` method. This method takes a `Flash[A]` object and returns the typed value of the flash message (`Option[A]`). To create a `Flash` object, we use the `Flash.get*` methods:

```scala mdoc:invisible
val request = Request()
```

```scala
request.flash(Flash.getNotice[String])
request.flash(Flash.getNotice[Int])

request.flash(Flash.getAlert[String])
request.flash(Flash.getAlert[CustomAlert])

request.flash(Flash.get[String]("security"))
request.flash(Flash.get[Int]("quota"))
request.flash(Flash.get[Notification]("notification"))
```

Let's take a look at list of setter methods:

| Method                                         | Output         | Description                                                                         |
|------------------------------------------------|----------------|-------------------------------------------------------------------------------------|
| `Flash.setValue[A: Schema](key: String, a: A)` | `Setter[A]`    | Sets a flash value of type `A` with the given key in the flash scope.               |
| `Flash.setNotice[A: Schema](a: A)`             | `Setter[A]`    | Sets a flash notice message with the provided value of type `A` in the flash scope. |
| `Flash.setAlert[A: Schema](a: A)`              | `Setter[A]`    | Sets a flash alert message with the provided value of type `A` in the flash scope.  |
| `Flash.setEmpty`                               | `Setter[Unit]` | Clears the flash scope by setting it to empty.                                      |

And here is the list of getter methods:

| Method                              | Output           | Description                                                    |
|-------------------------------------|------------------|----------------------------------------------------------------|
| `Flash.get[A: Schema](key: String)` | `Flash[A]`       | Gets any flash value of type `A` with the given key `key`.     |
| `Flash.getString(key: String)`      | `Flash[String]`  | Gets a flash value of type `String` with the given key `key`.  |
| `Flash.getNotice[A: Schema]`        | `Flash[A]`       | Gets a flash value of type `A` associated with the notice key. |
| `Flash.getAlert[A: Schema]`         | `Flash[A]`       | Gets a flash value of type `A` associated with the alert key.  |
| `Flash.getFloat(key: String)`       | `Flash[Float]`   | Gets a flash value of type `Float` with the given key `key`.   |
| `Flash.getDouble(key: String)`      | `Flash[Double]`  | Gets a flash value of type `Double` with the given key `key`.  |
| `Flash.getInt(key: String)`         | `Flash[Int]`     | Gets a flash value of type `Int` with the given key `key`.     |
| `Flash.getLong(key: String)`        | `Flash[Long]`    | Gets a flash value of type `Long` with the given key `key`.    |
| `Flash.getUUID(key: String)`        | `Flash[UUID]`    | Gets a flash value of type `UUID` with the given key `key`.    |
| `Flash.getBoolean(key: String)`     | `Flash[Boolean]` | Gets a flash value of type `Boolean` with the given key `key`. |
| `Flash.get[A: Schema]`              | `Flash[A]`       | Gets the first flash value of type `A` regardless of any key.  |

## Setting/Retrieving Multiple Flash Messages

Flash messages are composable, this means that we can have multiple flash messages and setting/retrieving them in a single request/response. Let's compose two setters and add them to a response:

```scala mdoc:compile-only
val setter: Flash.Setter[(String, String)] = 
  Flash.setNotice("The form was submitted successfully!") ++
    Flash.setAlert("You are reaching your quota!")
val response = Response.ok.addFlash(setter)
```

Then we can retrieve both messages from the request:

```scala mdoc:compile-only
val getter: Flash[(String, String)] = 
  Flash.getNotice[String] <*> Flash.getAlert[String]
val result: Option[(String, String)] = 
  request.flash(getter)
```

To render both messages, ZIO HTTP provides the `Flash#getMessage` and `Flash#getMessageHtml` which they return `Flash[Message[A, B]]` and `Flash[Message[Html, Html]]`. The `Message` represents both a **notice** and an **alert** message, so we can render it by folding both messages into a single value using the `Flash#foldHtml` method:

```scala mdoc:invisible
def renderNoFlash = Html.fromString("no-flash")
def renderNotice(html: Html): Html = div(styleAttr := "background: green", html)
def renderAlert(html: Html): Html = div(styleAttr := "background: red", html)
def renderBothMessage(notice: Html, alert: Html): Html = notice ++ alert
```

```scala mdoc:compile-only
val getBoth: Flash[Html] =
  Flash.getMessageHtml.foldHtml(renderNotice, renderAlert)(renderBothMessage)
val html = request.flash(getBoth).getOrElse(renderNoFlash)
```

<details>
<summary><b>Full Implementation Showcase</b></summary>

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.template._

object ui {
  def renderNoFlash = Html.fromString("no-flash")

  def renderNotice(html: Html): Html = div(styleAttr := "background: green", html)

  def renderAlert(html: Html): Html = div(styleAttr := "background: red", html)

  def renderBothMessage(notice: Html, alert: Html): Html = notice ++ alert
}

object SetGetBothFlashExample extends ZIOAppDefault {
  val routes = Routes(
    Method.GET / "set-flash" -> handler {
      val setBoth: Flash.Setter[(String, String)] =
        Flash.setNotice("The form was submitted successfully!") ++
          Flash.setAlert("You are reaching your quota!")
      Response
        .seeOther(URL.root / "get-flash")
        .addFlash(setBoth)
    },
    Method.GET / "get-flash" -> handler { (req: Request) =>
      val getBoth: Flash[Html] =
        Flash.getMessageHtml.foldHtml(ui.renderNotice, ui.renderAlert)(ui.renderBothMessage)
      Response.html(
        req.flash(getBoth).getOrElse(ui.renderNoFlash),
      )
    },
  ).sandbox

  def run = Server.serve(routes).provide(Server.default, Flash.Backend.inMemory)
}
```

</details>

## Flash Backends

### Cookie-based Flash-scope

By default, ZIO HTTP uses **cookie-based flash-scope** to store flash messages. This means that flash messages are stored in the session data as a cookie named `zio-http-flash`. Let's run a simple example and see what is stored in the session data:

```scala mdoc:compile-only
import zio._
import zio.http._

object CookieBasedFlashExample extends ZIOAppDefault {
  val routes = Routes(
    Method.GET / "set-flash" -> handler {
      Response
        .seeOther(URL.root / "get-flash")
        .addFlash(
          Flash.setNotice("The form was submitted successfully!"),
        )
    },
    Method.GET / "get-flash" -> handler { (req: Request) =>
      Response.text(
        req.flash(Flash.getNotice[String]).getOrElse("no-flash"),
      )
    },
  ).sandbox

  def run = Server.serve(routes).provide(Server.default)
}
```

After running the server and calling the `/set-flash` endpoint, we can inspect the headers of the response and see the `zio-http-flash` cookie:

```bash
$ curl -X GET http://127.0.0.1:8080/set-flash -i
HTTP/1.1 303 See Other
location: /get-flash
set-cookie: zio-http-flash=%7B%22notice%22%3A%22%5C%22The+form+was+submitted+successfully%21%5C%22%22%7D; Path=/
content-length: 0
```

We can see that the flash message is encoded and stored inside the `zio-http-flash` cookie. The browser will store this cookie and send it back to the server on subsequent requests. If we call the `/get-flash` endpoint with the `zio-http-flash` cookie, we will see the flash message:

```bash
$ curl -X GET http://127.0.0.1:8080/get-flash -H "cookie: zio-http-flash=%7B%22notice%22%3A%22%5C%22The+form+was+submitted+successfully%21%5C%22%22%7D; Path=/" -i
HTTP/1.1 200 OK
content-type: text/plain
content-length: 28

The form was submitted successfully!⏎
```

Using cookies to store flash messages has some limitations, such as the maximum size of the cookie, which is around 4KB.

### Backend-based Flash-scope

To overcome these limitations, ZIO HTTP provides a way to store flash messages in custom backends, such as in-memory, Redis, or any other custom backend. To use a backend, we need to get `Flash.Backend` service from the environment and use it to set and retrieve flash messages and finally, we should provide an implementation of the `Flash.Backend` trait to the HTTP application.

ZIO HTTP has a built-in in-memory backend that can be used to store flash messages in memory of the server, but still requires a cookie to store the identifier of the flash messages. To use the in-memory backend, we can provide the `Flash.Backend.inMemory` layer to our application:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.template._

object FlashBackendExample extends ZIOAppDefault {
  val routes = Routes(
    Method.GET / "set-flash" -> handler {
      for {
        flashBackend <- ZIO.service[Flash.Backend]
        response     <- flashBackend.addFlash(
          Response.seeOther(URL.root / "get-flash"),
          Flash.setNotice("The form was submitted successfully!"),
        )
      } yield response
    },
    Method.GET / "get-flash" -> handler { (req: Request) =>
      for {
        flashBackend <- ZIO.service[Flash.Backend]
        notice       <- flashBackend.flash(req, Flash.getNotice[String])
      } yield Response.text(notice)
    },
  ).sandbox

  def run = Server.serve(routes).provide(Server.default, Flash.Backend.inMemory)
}
```

By running the server and calling the `/set-flash` endpoint, we can see that the `zio-http-flash` cookie is only used to store the identifier of the flash messages (`flashId`), any other data is stored in server memory:

```bash
milad@nixos ~> curl -x GET http://127.0.0.1:8080/set-flash -i
HTTP/1.1 303 See Other
location: /get-flash
set-cookie: zio-http-flash=%7B%22flashId%22%3A%22%5C%22560c32c7-35c7-441e-9861-97562732db29%5C%22%22%7D; Path=/
content-length: 0
```

If we call the `/get-flash` endpoint with the `zio-http-flash` cookie, we will see the flash message:

```bash
$ curl -X GET http://127.0.0.1:8080/get-flash -H "cookie: zio-http-flash=%7B%22flashId%22%3A%22%5C%22560c32c7-35c7-441e-9861-97562732db29%5C%22%22%7D; Path=/" -i
HTTP/1.1 200 OK
content-type: text/plain
content-length: 28

The form was submitted successfully!
```

:::note
When we are using **cookie-based flash-scope**, we are responsible for removing (expiring) the flash messages from the session data after being read/displayed to the user. However, when we are using **in-memory backed**, the flash messages are automatically removed, so after the first read, we have no access to the flash messages anymore.
:::
