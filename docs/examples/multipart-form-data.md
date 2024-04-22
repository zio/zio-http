---
id: multipart-form-data
title: "Multipart Form Data Example"
sidebar_label: "Multipart Form Data"
---

## Multipart Form Data Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/MultipartFormData.scala")
```

**Explanation**

**This code enables file uploads to an HTTP server.**

**Server-Side**

* **Route:** Sets up a route (`/upload`) specifically for handling file uploads.
* **Form Handling:**  Checks the incoming data and carefully extracts the uploaded file from the request. 
* **Smart Responses:** Provides informative responses about the uploaded file or sends error messages if something is wrong with the upload.

**Client-Side**

* **Demo:** Shows how to use the HTTP client to send a file to the server's upload route.
* **Logging:** Records the server's response for easy review.

**Key Point:** This code demonstrates ZIO HTTP's ability to handle multipart/form-data, which is essential for file uploads in web applications.



## Multipart Form Data Streaming Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/MultipartFormDataStreaming.scala")
```

**Explanation**

**This code demonstrates different ways to handle file uploads on an HTTP server and includes helpful logging.**

**Server-Side**

* **Multiple Routes:**  Sets up routes like '/upload-simple', '/upload-nonstream', etc., each showcasing a different method for handling uploaded file data.
* **Focus on Streaming:** Many routes emphasize streaming techniques, which are efficient for handling large files.
* **Detailed Logging:** Logs when the server starts processing a file upload and when it finishes, along with the size of the data. This is great for debugging and monitoring.

**Key Points:**

* **Flexibility:** This code shows you various options for working with multipart form data in ZIO HTTP.
* **Efficiency:** Streaming-based approaches are ideal for situations where you might be dealing with large file uploads.
* **Debugging:** The logging makes it easier to understand how the server is processing incoming files.
