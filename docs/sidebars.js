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
            "examples/cli",
            "examples/cookies",
            "examples/concrete-entity"
            "examples/endpoint",
            "examples/graceful-shutdown",
            "examples/html-templating",
            "examples/multipart-form-data",
            {
              type: "category",
              label: "Basic Examples",
              collapsed: false,
              items: [
                "examples/basic/hello-world",
                "examples/basic/http-client",
                "examples/basic/https-client",
                "examples/basic/http-server",
                "examples/basic/https-server",
                "examples/basic/websocket",
              ]
            },
            {
              type: "category",
              label: "Advanced Examples",
              collapsed: false,
              items: [
                "examples/advanced/authentication-server",
                "examples/advanced/concrete-entity",
                "examples/advanced/middleware-basic-authentication",
                "examples/advanced/middleware-cors-handling",
                "examples/advanced/server",
                "examples/advanced/streaming",
                "examples/advanced/websocket"
              ]
            }
          ]
        }
      ]
    }
  ]
};

module.exports = sidebars;
