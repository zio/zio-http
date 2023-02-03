const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Http",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "setup",
        "getting-started",
        {
          type: "category",
          label: "DSL",
          collapsed: false,
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
            {
              type: "category",
              label: "DSL",
              collapsed: false,
              items: [
                "dsl/socket/socket",
                "dsl/socket/websocketframe"
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
            {
              type: "category",
              label: "Basic Examples",
              collapsed: false,
              items: [
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
                "examples/advanced/middleware-csrf",
                "examples/advanced/server",
                "examples/advanced/streaming-file",
                "examples/advanced/streaming-response",
                "examples/advanced/websocket-server"
              ]
            }
          ]
        }
      ]
    }
  ]
};

module.exports = sidebars;
