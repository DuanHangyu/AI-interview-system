/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}", // 包含 Vue + TS 文件
  ],
  theme: {
    extend: {
      screens: {
        "4xl": "1921px", // 自定义断点，大于1920px时应用
      },
    },
  },
  plugins: [],
};
