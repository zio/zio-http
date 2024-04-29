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
            "reference/endpoint", 

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
          ],
        },
        {
          type: "category",
          label: "Guides",
          link: { type: "doc", id: "index" },
          items: [
            "guides/integration-with-zio-config",
          ],
        },
        "faq",
        {
          // Subcategory: Tutorials 
          type: "category",
          label: "Tutorials",
          items: [
            "tutorials/testing-http-apps", 
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