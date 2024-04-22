---
id: html-templating
title: "HTML Templating Example"
sidebar_label: "HTML Templating"
---

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HtmlTemplating.scala")
```

**Explanation**

**This code shows how to generate dynamic HTML pages using ZIO HTML. Here's how it works:**

* **The `app` function:** Defines an HTTP server response that contains an HTML page.
* **ZIO HTML DSL:**  Builds the HTML structure using tags like `html`, `body`, and `li`. Think of it like  building blocks for your webpage.
* **Dynamic Content:**  Uses Scala code (`map` function) to create links that update automatically.
* **Customization:** Adds attributes (`css`, `styles`, `href`) to style and control the page's appearance and links. 

