---
id: serving-static-files
title: "Serving Static Files Example"
sidebar_label: "Serving Static Files"
---

## Serving Static Files

This example shows how to host static resources like images, CSS and JavaScript files using ZIO HTTP's built-in middleware:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/StaticFiles.scala")
```

**Explaination**

**This code makes static files within your 'resources/static' directory available on your ZIO HTTP server. Here's the breakdown:**

* **No initial routes:** The app starts with no predefined routes (`Routes.empty`).
* **Middleware does the work:** The `Middleware.serveResources` takes care of serving the static files.
* **Path mapping:**  Requests that start with `/static` are automatically directed to the correct files within the 'resources/static' directory.  

**Key Point:** With this setup, users can access your static files (like images, CSS, JavaScript) directly through your server. 




## Serving Static Resource Files

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/StaticServer.scala")
```

**Explaination**
**This code sets up a ZIO HTTP server that does two things:**

1. **Serves Static Files:** It allows users to access files directly from a local directory on your server (like images, CSS, etc.)

2. **Provides a File Explorer:** It creates a simple web interface to browse the files in the directory.

**How it works:**

* **Routes:** The code defines a route that handles requests to `/static/*` 
* **Smart Handling:** It checks if the requested path is a file or a directory and responds accordingly:
    * **Directory:** Lists the files in a user-friendly way
    * **File:** Sends the file content to the user
    * **Missing File:** Sends a 'Not Found' error (404) 