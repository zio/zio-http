const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO HTTP",
      collapsed: true,

      // main documentation index
      link: { type: "doc", id: "index" },
      items: [
        "installation",
        // Concepts section
        {
            type: "category",
            collapsed: true,
            label: "Concepts",
            items: [
                "concepts/routing",
                "concepts/middleware",
                "concepts/endpoint",
                ],

        },
        // Reference section
        {
          type: "category",
          collapsed: true,
          link: { type: "doc", id: "reference/index" },
          label: "Reference",
          items: [
            "reference/overview",
            "reference/server",
            "reference/client",

            // Routing subsection
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

            // HTTP Messages subsection
            {
              type: "category",
              label: "HTTP Messages",
              items: [
                "reference/request",
                "reference/response/response",
                "reference/response/status",

                // Headers subsection
                {
                  type: "category",
                  label: "Headers",
                  items: [
                    "reference/headers/headers",
                    "reference/headers/session/cookies",
                    "reference/headers/session/flash",
                  ],
                },

                // Message Body subsection
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
              ],
            },
            {
              type: "category",
              label: "Declarative Endpoints",
              items: [
                "reference/endpoint",
                "reference/http-codec",
              ],
            },

            // Aspects subsection
            {
              type: "category",
              label: "Aspects",
              items: [
                "reference/aop/protocol-stack",
                "reference/aop/middleware",
                "reference/aop/handler_aspect",
              ],
            },

            // WebSocket subsection
            {
              type: "category",
              label: "WebSocket",
              items: [
                "reference/socket/socket",
                "reference/socket/websocketframe",
              ],
            },

            // Configs subsection
            {
              type: "category",
              label: "Configs",
              items: [
                "reference/configs/introduction",
                "reference/configs/connectionpoolconfig",
                "reference/configs/dnsresolver-config",
                "reference/configs/server-config",
                "reference/configs/gen-openapi-config",
                "reference/configs/netty-nettyconfig",
              ]
            }
          ],
        },
        {
          type: "category",
          label: "Guides",
          link: { type: "doc", id: "index" },
          items: [
            "guides/integration-with-zio-config",
            "guides/testing-http-apps",
          ],
        },

        // Examples section
        {
          type: "category",
          label: "Examples",
          link: { type: "doc", id: "examples/index" },
          items: [
            "examples/hello-world",
            "examples/http-client-server",
            "examples/https-client-server",
            "examples/serving-static-files",
            "examples/html-templating",
            "examples/websocket",
            "examples/streaming",
            "examples/endpoint",
            "examples/endpoint-scala3",
            "examples/middleware-cors-handling",
            "examples/authentication",
            "examples/graceful-shutdown",
            "examples/cli",
            "examples/concrete-entity",
            "examples/multipart-form-data",
            "examples/server-sent-events-in-endpoints",
          ],
        },
        "faq",
      ],
    },
  ],
};

module.exports = sidebars;
