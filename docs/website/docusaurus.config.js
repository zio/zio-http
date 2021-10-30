// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require('prism-react-renderer/themes/github');
const darkCodeTheme = require('prism-react-renderer/themes/dracula');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'ZIO-HTTP',
  tagline: 'Scala library for building http apps',
  url: 'https://github.com/dream11',
  baseUrl: '/zio-http/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  organizationName: 'dream11', // Usually your GitHub org/user name.
  projectName: 'zio-http', // Usually your repo name.

  presets: [
    [
      '@docusaurus/preset-classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          // Please change this to your repo.
        },
        blog: {
          showReadingTime: true,
          // Please change this to your repo.
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
      navbar: {
        title: 'ZIO-HTTP',
        items: [
          {
            type: 'doc',
            docId: 'index',
            position: 'left',
            label: 'Tutorial',
          }
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Documents',
                to: '/docs/index',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'Discord',
                href: 'https://discord.com/channels/629491597070827530/819703129267372113',
              }
            ],
          }
        ]
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
        additionalLanguages: ["java", "scala"]
      },
    }),
};

module.exports = config;
