const sidebars = {

  sidebar: [
    {

      type: "category",
      label: "ZIO HTTP",
      collapsed: true,
      link: { type: "doc", id: "index" },
      items: [
        "installation", 
        "overview", 

        {
          // Subcategory: Reference
          type: "category",
          collapsed: true,
          label: "Reference",
          items: [
            "reference/server", 
            "reference/client",
            "reference/template",
            "reference/handler",
            "reference/endpoint",

            {
              // Subcategory: Routing
              type: "category",
              label: "Routing",
              items: [
                "reference/routes", 
                "reference/route_pattern", 
                "reference/path_codec", 
              ],
            },
            

            {
              // Subcategory: HTTP Messages
              type: "category",
              label: "HTTP Messages",
              items: [
                {
                  // Sub-subcategory: Headers
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
                  // Sub-subcategory: Response
                  type: "category",
                  label: "Response",
                  items: ["reference/response", "reference/status"], 
                },

                {
                  // Sub-subcategory: Message Body
                  type: "category",
                  label: "Message Body",
                  items: ["reference/body", "reference/form"],
                },
              ],
            }, 

            {
              // Subcategory: HTTP Middleware
              type: "category",
              label: "HTTP Middleware",
              items: [
                "reference/protocol-stack",
                "reference/middleware", 
                "reference/handler_aspect", 
              ],
            },

            {
              // Subcategory: WebSocket
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
          // Subcategory: Tutorials 
          type: "category",
          label: "Tutorials",
          items: [
            "tutorials/testing-http-apps", 
          ],
        },

        {
          // Subcategory: Examples
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

        "binary_codecs",
        "faq",
      ],
    },
  ],
};

module.exports = sidebars;
