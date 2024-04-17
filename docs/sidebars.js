const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Http",
      collapsed: true,
      link: { type: "doc", id: "index" },

      items: [
        "setup",
        "quickstart",
        "performance",
        {
          type: "category",
          label: "Concepts",
          link: { type: "doc", id: "index" },
          items: [
            "concepts/routing",
            "concepts/request-handling",
            "concepts/server",
            "concepts/client",
            "concepts/middleware",
            "concepts/endpoint",
          ],
        },
        {
          type: "category",
          label: "Tutorials",
          collapsed: false,
          link: { type: "doc", id: "index" },
          items: [
            "tutorials/testing-your-zio-http-app",
            "tutorials/deeper-dive-into-middleware",
          ],
        },
        {
          type: "category",
          label: "How-to-guides",
          collapsed: false,
          link: { type: "doc", id: "index" },
          items: [
            "how-to-guides/endpoint",
            "how-to-guides/cookie-authentication",
            "how-to-guides/basic-web-application-with-zio-http",
            "how-to-guides/multipart-form-data",
            "how-to-guides/how-to-utilize-signed-cookies",
            "how-to-guides/how-to-handle-WebSocket-exceptions-and-errors",
          ],
        },
        {
          type: "category",
          label: "Reference",
          collapsed: false,
          link: { type: "doc", id: "index" },
          items: [
            "reference/server-backend",
            "reference/websockets",
            "reference/json-handling",
            "reference/metrics",
            "reference/request-logging",
            {
              type: "category",
              label: "DSL",
              link: { type: "doc", id: "index" },
              items: [
                "dsl/server",
                "dsl/routes",
                "dsl/route_pattern",
                "dsl/path_codec",
                "dsl/request",
                "dsl/response",
                "dsl/handler",
                "dsl/headers",
                "dsl/body",
                "dsl/form",
                "dsl/cookies",
                "dsl/flash",
                "dsl/protocol-stack",
                "dsl/middleware",
                "dsl/handler_aspect",
                "dsl/status",
                {
                  type: "category",
                  label: "DSL",
                  collapsed: false,
                  items: [
                    "dsl/socket/socket",
                    "dsl/socket/websocketframe"
                  ]
                },
                "dsl/template",
                "dsl/client"
              ]
            }
          ]
        },
        {
          type: "category",
          label: "Examples",
          collapsed: false,
          link: { type: "doc", id: "index" },
          items: [
            "examples/hello-world",
            "examples/http-client-server",
            "examples/https-client-server",
            "examples/serving-static-files",
            "examples/html-templating",
            "examples/websocket",
            "examples/streaming",
            "examples/endpoint",
            "examples/cors-handling-middleware",
            "examples/authentication",
            "examples/graceful-shutdown",
            "examples/cli",
            "examples/concrete-entity",
            "examples/multipart-form-data",
            "examples/server-sent-events-in-endpoints",
          ],
        },
      ],
    },
  ],
};


