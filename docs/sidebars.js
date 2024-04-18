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
              "quickstart",
      
              
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
            "tutorials/testing-your-zio-http-app",
            "tutorials/deeper-dive-into-middleware",
          ],
    },
    
    // How to guide
    {
          type: "category",
          label: "How-to-guides",
          items: [
            "how-to-guides/endpoint",
            "how-to-guides/cookie-authentication",
            "how-to-guides/basic-web-application-with-zio-http",
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
        "examples/cors-handling-middleware",
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
        
        //reference/dsl
        {
          type: "category",
          label: "DSL",
          items: [
            "reference/dsl/server",
            "reference/dsl/routes",
            "reference/dsl/route_pattern",
            "reference/dsl/path_codec",
            "reference/dsl/request",
            "reference/dsl/response",
            "reference/dsl/handler",
            "reference/dsl/headers",
            "reference/dsl/body",
            "reference/dsl/form",
            "reference/dsl/cookies",
            "reference/dsl/flash",
            "reference/dsl/protocol-stack",
            "reference/dsl/middleware",
            "reference/dsl/handler_aspect",
            "reference/dsl/status",
            "reference/dsl/socket/socket",
            "reference/dsl/socket/websocketframe",
            "reference/dsl/template",
            "reference/dsl/client",
          ],
        },       
      ],    
    },
      
            
      "performance",
      "faq",],
    },
  ],
};

module.exports = sidebars;
