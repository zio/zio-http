// Prism theme of the homepage design: the same navy panel and lime / purple /
// teal token colors in light and dark mode. Used for `prism.theme` and
// `prism.darkTheme` in docusaurus.config.js.
module.exports = {
  plain: {
    color: '#C6CCDA',
    backgroundColor: '#0B1020',
  },
  styles: [
    {
      types: ['keyword', 'boolean', 'important'],
      style: {color: '#B8E62A'},
    },
    {
      types: ['class-name', 'builtin', 'namespace', 'constant', 'symbol'],
      style: {color: '#A78BFA'},
    },
    {
      types: ['string', 'char', 'number', 'attr-value', 'template-string'],
      style: {color: '#4FD1C5'},
    },
    {
      types: ['comment', 'prolog', 'doctype', 'cdata'],
      style: {color: '#7E88A0'},
    },
    {
      types: ['punctuation', 'operator'],
      style: {color: '#C6CCDA'},
    },
  ],
};
