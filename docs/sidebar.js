const sidebars = {
  sidebar: [
    {
      
      type: "category",
      label: "ZIO Http",
      collapsed: false,
      link: { type: "doc", id: "index" },
      
      items: [
        "setup",
        "quickstart",
      {
        type: "category",
        label: "Concepts",
        collapsed: false,
        link: { type: "doc", id: "index" },
        items: [
          "concepts/routing",
          "concepts/request-handling",
          "concepts/server",
          "concepts/client",
          "concepts/middleware",
          "concepts/endpoint"
        ]
    },
      {
      type: "category",
      label: "Tutorials",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "tutorials/your-first-zio-http-app",
        "tutorials/deploying-a-zio-http-app",
        "tutorials/testing-your-zio-http-app"
      ]
    },
      {
      type: "category",
      label: "How-to-guides",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "how-to-guides/endpoint",
        "how-to-guides/middleware",
      ]
    },
     {
      type: "category",
      label: "Reference",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "reference/api-docs",
        "reference/server-backend",
        "reference/websockets",
        "reference/json-handling",
        "reference/metrics",
        "reference/request-logging"
      ]
    },
     {
      type: "category",
      label: "Performance",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: ["performance"]
    },
    {
      type: "category",
      label: "FAQ",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: ["faq"]
    }
      ]
      
    }
  ]
};