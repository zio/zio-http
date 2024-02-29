---
id: form
title: Form
---

The `Form` represents a collection of `FormFields` that can be a multipart or URL-encoded form:

```scala
final case class Form(formData: Chunk[FormField])
```

A `Form` is commonly used in request bodies for handling data from HTML forms and file uploads, although it can also be utilized in response bodies.

## Form Fields

A `FormField` is a field within a `Form` and consists of a name, content type, type-specific content, and an optional filename.

```scala
sealed trait FormField {
  def name: String
  def contentType: MediaType
  def filename: Option[String]
}
```

There are four types of `FormField`: Simple FormField, Text FormField, Binary FormField, and StreamingBinary FormField.

### Simple FormField

Simple form fields are represented by the `Simple` case class. They consist of a simple key-value pair containing a name and a value (String). Unlike Binary and Text, they do not contain additional metadata such as content type or filename:

```scala
final case class Simple(name: String, value: String) extends FormField {
  override val contentType: MediaType   = MediaType.text.plain
  override val filename: Option[String] = None
}
```

To create a simple form field, we can use `FormField.simpleField` constructor:

```scala mdoc:compile-only
import zio.http._

val simpleFormField = FormField.simpleField("name", "value")
```

Instances of `FormField.Simple` are commonly used for transmitting simple textual data where additional metadata is not required, such as form fields in HTML forms.

### Text FormField

Text form fields are represented by the `Text` case class. They contain textual data (String) along with metadata such as the content type and optionally the filename:

```scala
final case class Text(
  name: String,
  value: String,
  contentType: MediaType,
  filename: Option[String] = None,
) extends FormField
```

To create a text form field, we can use `FormField.textField` constructor:

```scala mdoc:compile-only
import zio.http._

val textFormField1 = FormField.textField("name", "value")

val textFormField2 = FormField.textField("name", "value", MediaType.text.plain)
```

Instances of `FormField.Text` are used for transmitting simple textual data and textual files, such as text files, HTML files, and so on.

### Binary FormField

Binary form fields are represented by the `FormField.Binary` case class. They contain binary data (`Chunk[Byte]`), along with metadata such as the content type (`MediaType`) and optionally the `Content-Transfer-Encoding` header field and filename:

```scala
final case class Binary(
  name: String,
  data: Chunk[Byte],
  contentType: MediaType,
  transferEncoding: Option[ContentTransferEncoding] = None,
  filename: Option[String] = None,
) extends FormField
```

To create a binary form field, we can use `FormField.binaryField` constructor:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.Header._

val image = Chunk.fromArray(???)

val binaryFormField = FormField.binaryField(
  name = "profile pic",
  data = image,
  mediaType = MediaType.image.jpeg,
  transferEncoding = Some(ContentTransferEncoding.Binary),
  filename = Some("profile.jpg")
)
```

:::note
The data is not encoded in any way relative to the provided `transferEncoding`. It is the responsibility of the user to encode the `data` accordingly.
:::

This form field is suitable for transmitting files or other binary data through HTTP requests.

The data is typically encoded in a way that can be transmitted as text (e.g., Base64 encoding) and decoded on the receiving end. The transfer encoding can be one of the following: `SevenBit`, `EightBit`, `Binary`, `QuotedPrintable`, `Base64`, and `XToken`.

## Creating a Form

The `Form`'s companion object offers several convenient methods for constructing form data, whether from individual form fields, key-value pairs, multipart bytes, query parameters, or URL-encoded data. We'll cover each of these methods and provide examples to illustrate their usage.

### Creating an Empty Form

We can create an empty form using the empty method:

```scala mdoc:compile-only
import zio.http._

val emptyForm = Form.empty
```

This creates an empty form with no fields.

### Creating a Form from Form Fields

We can create a form by providing individual form fields using the `Form.apply` method. This method takes one or more FormField objects:

```scala mdoc:compile-only
import zio.http._

val form = Form(
  FormField.Simple("name", "John"),
  FormField.Simple("age", "42"),
)
```

### Creating a Form from Key-Value Pairs

We can create a form from key-value pairs using the `Form.fromStrings` method:

```scala mdoc:compile-only
import zio.http._

val formData = Form.fromStrings(
  "username" -> "johndoe",
  "password" -> "secret",
)
```

### Decoding Raw Multipart Bytes into a Form

We can create a form from multipart bytes using the `Form.fromMultipartBytes` method. This is useful when handling multipart form data received in HTTP requests.

Assume we have received the following multipart data:

```scala mdoc:silent
import zio.http._

val multipartBytes =
  s"""|--boundary123\r
      |Content-Disposition: form-data; name="field1"\r
      |\r
      |value1\r
      |--boundary123\r
      |Content-Disposition: form-data; name="field2"\r
      |\r
      |value2\r
      |--boundary123\r
      |Content-Disposition: form-data; name="file1"; filename="filename1.txt"\r
      |Content-Type: text/plain\r
      |\r
      |Contents of filename1.txt\r
      |--boundary123\r
      |Content-Disposition: form-data; name="file2"; filename="filename2.txt"\r
      |Content-Type: text/plain\r
      |\r
      |Contents of filename2.txt\r
      |--boundary123--\r\n""".stripMargin.getBytes(Charsets.Utf8)
```

We can decode it with the following code:

```scala mdoc:compile-only
import zio._
import zio.http._

val charset = Charsets.Utf8

val formTask: Task[Form] =
  Form.fromMultipartBytes(Chunk.fromArray(multipartBytes), charset, Some(Boundary("boundary123")))

val formData: Task[Chunk[FormField]] = formTask.map(_.formData)
```

### Creating a Form from Query Parameters

We can create a form from query parameters using the `Form.fromQueryParams` method:

```scala mdoc:compile-only
import zio.http._

val queryParams: QueryParams = QueryParams(
    "name" -> "John",   
    "age" -> "42"       
  )

val form = Form.fromQueryParams(queryParams)
```

### Creating a Form from URL-Encoded Data

We can create a form from URL-encoded data using the `Form.fromURLEncoded` method:

```scala mdoc
import zio.http._

val encodedData: String = "username=johndoe&password=secret" 

val formResult: Either[FormDecodingError, Form] =
  Form.fromURLEncoded(encodedData, Charsets.Utf8)
```
