const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO HTTP",
      collapsed: true,
      link: { type: "doc", id: "index" },
      items: [
        "overview",
    
        //concepts          
        {
          type: "category",
          label: "Concepts",
          link: { type: "doc", id: "concepts/intro" }, 
          items: [
            "concepts/client",
            "concepts/endpoint",
            "concepts/middleware",
            "concepts/request-handling",
            "concepts/routing",
            "concepts/server",
          ],
        },
    
        // detailed tutorials
        {
          type: "category",
          label: "Tutorials",
          items: [
            "tutorials/testing-http-apps",
          ],
        },
    
        // How to guide
        {
          type: "category",
          label: "How-to-guides",
          items: [
            "how-to-guides/cookie-authentication",
            "how-to-guides/how-to-use-html-templating",
            "how-to-guides/multipart-form-data",
            "how-to-guides/how-to-utilize-signed-cookies",
            "how-to-guides/how-to-handle-WebSocket-exceptions-and-errors",
          ],
        },


        // Examples
        {
          type: "category",
          label: "Examples",
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
    
        //Reference
        {
          type: "category",
          label: "Reference",
          items: [
            "reference/server-backend",
            "reference/websockets",
            "reference/json-handling",
            "reference/metrics",
            "reference/request-logging",
            "reference/server",
            "reference/routes",
            "reference/route_pattern",
            "reference/path_codec",
            "reference/request",
            "reference/response",
            "reference/handler",
            "reference/headers",
            "reference/body",
            "reference/form",
            "reference/cookies",
            "reference/flash",
            "reference/protocol-stack",
            "reference/middleware",
            "reference/handler_aspect",
            "reference/status",
            "reference/socket/socket",
            "reference/socket/websocketframe",
            "reference/template",
            "reference/client",
            "reference/endpoint",
          ],
        },       
      ],    
    },
    
    "performance",
    "faq",
    "binary_codecs",
    "installation",
  ],
};

module.exports = sidebars;
