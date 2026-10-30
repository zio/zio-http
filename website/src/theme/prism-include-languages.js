import siteConfig from '@generated/docusaurus.config';
export default function prismIncludeLanguages(PrismObject) {
  const {
    themeConfig: {prism},
  } = siteConfig;
  const {additionalLanguages} = prism;
  // Prism components work on the Prism instance on the window, while prism-
  // react-renderer uses its own Prism instance. We temporarily mount the
  // instance onto window, import components to enhance it, then remove it to
  // avoid polluting global namespace.
  // You can mutate PrismObject: registering plugins, deleting languages... As
  // long as you don't re-assign it
  globalThis.Prism = PrismObject;
  additionalLanguages.forEach((lang) => {
    // eslint-disable-next-line global-require, import/no-dynamic-require
    require(`prismjs/components/prism-${lang}`);
  });

  require('prismjs/components/prism-java');
  require('prismjs/components/prism-scala');

  // Prism's Scala grammar only tags package names as types. The design colors
  // every type or object name (Routes, Request, ZIOAppDefault, ZIO), so tag
  // those too; all-caps names such as GET stay plain.
  PrismObject.languages.insertBefore('scala', 'keyword', {
    'class-name': /\b(?:ZIO|[A-Z][A-Za-z0-9_]*[a-z][A-Za-z0-9_]*)\b/,
  });

  delete globalThis.Prism;
}
