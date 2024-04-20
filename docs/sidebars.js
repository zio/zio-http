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
          label: "Reference",
          items: [
            "reference/server",
            "reference/routes",
            "reference/route_pattern",
            "reference/path_codec",
            "reference/request",
            "reference/response",
            "reference/handler",
            "reference/headers",
            "reference/body",
            "reference/endpoint",
            "reference/form",
            "reference/cookies",
            "reference/flash",
            "reference/protocol-stack",
            "reference/middleware",
            "reference/handler_aspect",
            "reference/status",
            {
              type: "category",
              label: "WebSocket",
              collapsed: false,
              items: [
                "reference/socket/socket",
                "reference/socket/websocketframe"
              ]
            },
            "reference/template",
            "reference/client"
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
