const colors = require('tailwindcss/colors');

/** @type {import('tailwindcss').Config} */
module.exports = {
  important: true,
  content: ['./src/**/*.{js,jsx,ts,tsx}', './docs/**/*.mdx'],
  darkMode: ['class', '[data-theme="dark"]'], // hooks into docusaurus' dark mode
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: colors.green[600],
          ...colors.green,
        },
      },
    },
  },
  plugins: [],
};
