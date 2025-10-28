// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require('prism-react-renderer/themes/github');
const darkCodeTheme = require('prism-react-renderer/themes/dracula');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'ZIO HTTP',
  tagline: ' A next-generation Scala framework for building scalable, correct, and efficient HTTP clients and servers',
  url: 'https://zio-http.netlify.app',
  baseUrl: '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.png',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'facebook', // Usually your GitHub org/user name.
  projectName: 'docusaurus', // Usually your repo name.

  // Even if you don't use internalization, you can use this field to set useful
  // metadata like html lang. For example, if your site is Chinese, you may want
  // to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          routeBasePath: '/', // Serve the docs at the site's root
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl:
            'https://github.com/zio/zio-http/tree/main/docs/',
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({

    algolia: {
      // The application ID provided by Algolia
      appId: '85XOOT8048',

      // Public API key: it is safe to commit it
      apiKey: '84fff34c3416e7232bc33705abd01f6c',

      indexName: 'zio-http',

      // Optional: see doc section below
      contextualSearch: true,

      // Optional: path for search page that enabled by default (`false` to disable it)
      searchPagePath: 'search',

      // Optional: whether the insights feature is enabled or not on Docsearch (`false` by default)
      insights: false,

    },

      navbar: {
        title: 'ZIO HTTP',
        logo: {
          alt: 'ZIO HTTP Logo',
          src: 'img/ZIO.png',
        },
        items: [
          {
            type: 'doc',
            docId: 'index',
            position: 'left',
            label: 'Getting Started',
          },
          {
            type: 'doc',
            docId: "reference/overview",
            position: 'left',
            label: 'Reference',
          },
          {
            type: 'doc',
            docId: "guides/integration-with-zio-config",
            position: 'left',
            label: 'Guides',
          },
          {
            type: 'doc',
            docId: "examples/hello-world",
            position: 'left',
            label: 'Examples',
          },
//          {to: '/blog', label: 'Blog', position: 'left'},
          {
            type: 'doc',
            docId: "faq",
            position: 'right',
            label: 'FAQ',
          },
          {
            href: 'https://github.com/zio/zio-http',
            label: 'GitHub',
            position: 'right',
          },

        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Getting Started',
                to: '/installation',
              },
              {
                label: 'Reference',
                to: '/reference/overview',
              },
              {
                label: 'Guides',
                to: '/guides/integration-with-zio-config',
              },
              {
                label: 'Examples',
                to: '/examples/hello-world',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'Stack Overflow',
                href: 'https://stackoverflow.com/questions/tagged/zio-http',
              },
              {
                label: 'Discord',
                href: 'https://discord.com/channels/629491597070827530/819703129267372113',
              }
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'GitHub',
                href: 'https://github.com/zio/zio-http',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} ZIO Maintainers. Built with Docusaurus.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
        additionalLanguages: ['java', 'scala'],
      },
      docs: {
        sidebar: {
          autoCollapseCategories: true,
          hideable: true,
        },
      },
    }),
};

module.exports = config;
