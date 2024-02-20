const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Http",
      collapsed: true,
      link: { type: "doc", id: "index" },
      items: [
        "setup",
        "getting-started",
        {
          type: "category",
          label: "DSL",
          link: { type: "doc", id: "index" },
          items: [
            "dsl/server",
            "dsl/routes",
            "dsl/request",
            "dsl/response",
            "dsl/handler",
            "dsl/body",
            "dsl/headers",
            "dsl/cookies",
            "dsl/middleware",
            {
              type: "category",
              label: "DSL",
              collapsed: false,
              items: [
                "dsl/socket/socket",
                "dsl/socket/websocketframe"
              ]
            },
            "dsl/template"
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
            "examples/middleware-cors-handling",
            "examples/authentication",
            "examples/cookies",
            "examples/graceful-shutdown",
            "examples/cli",
            "examples/concrete-entity",
            "examples/multipart-form-data",
            "examples/server-sent-events-in-endpoints",
          ]
        }
      ]
    }
  ]
};

module.exports = sidebars;
