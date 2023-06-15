const sidebars = {
  sidebar: [
    {
      
      type: "category",
      label: "ZIO Http",
      collapsed: false,
      link: { type: "doc", id: "index" },
      
      items: [
        "setup",
        "quickstart",
        "performance",
        "faq",
      {
        type: "category",
        label: "Concepts",
        collapsed: false,
        link: { type: "doc", id: "index" },
        items: [
          "concepts/routing",
          "concepts/request-handling",
          "concepts/server",
          "concepts/client",
          "concepts/middleware",
          "concepts/endpoint"
        ]
    },
      {
      type: "category",
      label: "Tutorials",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "tutorials/your-first-zio-http-app",
        "tutorials/deploying-a-zio-http-app",
        "tutorials/testing-your-zio-http-app",
        "tutorials/deeper-dive-into-middleware",
      ]
    },
      {
      type: "category",
      label: "How-to-guides",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "how-to-guides/advance-http-sever",
        "how-to-guides/http-client",
        "how-to-guides/http-sever",
        "how-to-guides/https-server",
        "how-to-guides/https-client",
        "how-to-guides/middleware-basic-authentication",
        "how-to-guides/middleware-cors-handling",
        "how-to-guides/streaming-file",
        "how-to-guides/streaming-response",
        "how-to-guides/websocket",
        "how-to-guides/endpoint",
        "how-to-guides/middleware",
        "how-to-guides/concrete-entity",
        "how-to-guides/cookie-authentication",
        "how-to-guides/basic-web-application-with-zio-http",
        "how-to-guides/multipart-form-data",
        "how-to-guides/how-to-utilize-signed-cookies",
        "how-to-guides/how-to-handle-WebSocket-exceptions-and-errors",
      ]
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
            "dsl/http",
            "dsl/request",
            "dsl/response",
            "dsl/body",
            "dsl/headers",
            "dsl/cookies",
            "dsl/middleware",
            "dsl/html",
            {
              type: "category",
              label: "DSL",
              collapsed: false,
              items: [
                "dsl/socket/socket",
                "dsl/socket/websocketframe"
              ]
            },
          ]
        },
      ]
    }
      ] 
    }
  ]
};