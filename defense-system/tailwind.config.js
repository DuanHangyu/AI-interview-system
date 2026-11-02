/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./public/index.html", "./src/**/*.{vue,js,ts}"],
  // 基础样式由 ant-design-vue 的 reset.css 提供，关闭 preflight 以免覆盖组件样式
  corePlugins: { preflight: false },
  theme: {
    extend: {
      colors: {
        primary: "#0041b7",
      },
    },
  },
  plugins: [],
};
