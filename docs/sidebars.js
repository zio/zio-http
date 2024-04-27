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
                "reference/routing/routes",
                "reference/routing/route_pattern",
                "reference/routing/path_codec",
              ],
            },
            "reference/handler",
            {
              type: "category",
              label: "HTTP Messages",
              items: [
                "reference/request",
                "reference/response/response",
                {
                  type: "category",
                  label: "Headers",
                  items: [
                    "reference/headers/headers",
                    "reference/headers/session/cookies",
                    "reference/headers/session/flash",
                  ],
                },
                {
                  type: "category",
                  label: "Message Body",
                  items: [
                    "reference/body/body",
                    "reference/body/form",
                    "reference/body/binary_codecs",
                    "reference/body/template",
                  ],
                },
                "reference/response/status",
              ],
            },
            "reference/endpoint",
            {
              type: "category",
              label: "Aspects",
              items: [
                "reference/aop/protocol-stack",
                "reference/aop/middleware",
                "reference/aop/handler_aspect",
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
          ],
        },
        {
          type: "category",
          label: "Guides",
          link: { type: "doc", id: "index" },
          items: [
            "guides/testing-http-apps",
            "guides/integration-with-zio-config",
          ],
        },
        "faq",
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
