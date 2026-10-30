// Prism themes with the code colors of the ZIO HTTP handoff palette
// (theme-tokens.css, --zh-code-*): light mode on the light surface, dark mode
// on the navy one. Used for `prism.theme` and `prism.darkTheme` in
// docusaurus.config.js; the panel itself is styled in src/css/custom.css.
const theme = ({bg, text, keyword, type, string, comment}) => ({
  plain: {color: text, backgroundColor: bg},
  styles: [
    {types: ['keyword', 'boolean', 'important'], style: {color: keyword}},
    {types: ['class-name', 'builtin'], style: {color: type}},
    {
      types: ['string', 'char', 'number', 'attr-value', 'template-string'],
      style: {color: string},
    },
    {types: ['comment', 'prolog', 'doctype', 'cdata'], style: {color: comment}},
    {types: ['punctuation', 'operator'], style: {color: text}},
  ],
});

module.exports = {
  light: theme({
    bg: '#F5F7FA',
    text: '#0B1020',
    keyword: '#0B7A50',
    type: '#4C51BF',
    string: '#8A5A00',
    comment: '#6B7385',
  }),
  dark: theme({
    bg: '#0B1020',
    text: '#C6CCDA',
    keyword: '#3DDC97',
    type: '#A78BFA',
    string: '#4FD1C5',
    comment: '#7E88A0',
  }),
};
