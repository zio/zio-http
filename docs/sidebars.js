const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO HTTP",
      collapsed: true,
      link: { type: "doc", id: "index" },
      items: [
        "installation",
        {
          type: "category",
          collapsed: true,
          link: { type: "doc", id: "reference/index" },
          label: "Reference",
          items: [
            "reference/server",
            "reference/client",
            {
              type: "category",
              label: "Routing",
              items: [
                "reference/routes",
                "reference/route_pattern",
                "reference/path_codec",
              ],
            },
            "reference/handler",
            {
              type: "category",
              label: "HTTP Messages",
              items: [
                {
                  type: "category",
                  label: "Headers",
                  items: [
                    "reference/headers",
                    "reference/cookies",
                    "reference/flash",
                  ],
                },
                "reference/request",
                {
                  type: "category",
                  label: "Response",
                  items: ["reference/response", "reference/status"],
                },
                {
                  type: "category",
                  label: "Message Body",
                  items: [
                    "reference/body",
                    "reference/form",
                    "reference/binary_codecs",
                  ],
                },
              ],
            },
            "reference/endpoint",
            {
              type: "category",
              label: "HTTP Middleware",
              items: [
                "reference/protocol-stack",
                "reference/middleware",
                "reference/handler_aspect",
              ],
            },
            {
              type: "category",
              label: "WebSocket",
              items: [
                "reference/socket/socket",
                "reference/socket/websocketframe",
              ],
            },
            "reference/template",
          ],
        },
        "testing-http-apps",
        {
          type: "category",
          label: "Examples",
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
          ],
        },
      ],
    },
  ],
};

module.exports = sidebars;
