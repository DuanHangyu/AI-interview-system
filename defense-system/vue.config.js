// vue.config.js
const { defineConfig } = require("@vue/cli-service");
const CompressionPlugin = require("compression-webpack-plugin");

module.exports = defineConfig({
  devServer: {
    port: 3002,
    client: {
      overlay: false, // 禁用错误遮罩层
    },
    proxy: {
      "/dev-api": {
        target: "http://localhost:8081/",
        changeOrigin: true,
        pathRewrite: {
          "^/dev-api": "",
        },
      },
    },
  },
  // Webpack 链式配置
  // chainWebpack: (config) => {
  //   // 移除 prefetch 插件，避免预加载所有异步 chunk
  //   config.plugins.delete('prefetch')

  //   // 生产环境配置
  //   if (process.env.NODE_ENV === 'production') {
  //     // 开启 Gzip 压缩
  //     config.plugin('compression').use(CompressionPlugin, [{
  //       algorithm: 'gzip',
  //       test: /\.(js|css|html|json|ico|svg)(\?.*)?$/i,
  //       threshold: 10240,
  //       minRatio: 0.8,
  //       deleteOriginalAssets: false
  //     }])

  //     // 打包分析工具（默认不开启，需要时取消注释）
  //     // config.plugin('webpack-bundle-analyzer').use(BundleAnalyzerPlugin)
  //   }
  // },
  // // 配置 Webpack
  // configureWebpack: {
  //   optimization: {
  //     splitChunks: {
  //       chunks: "all",
  //       maxSize: 244 * 1024, // 244KB
  //     },
  //     minimize: true,
  //   },
  // },

  chainWebpack: (config) => {
    // 移除默认的预加载插件（如果需要完全自定义）
    config.plugins.delete("prefetch");
    // config.plugins.delete("preload");

    config.resolve.alias.set(
      "axios",
      require.resolve("axios/dist/browser/axios.cjs")
    );

    config.plugin("define").tap((definitions) => {
      Object.assign(definitions[0], {
        __VUE_OPTIONS_API__: "true",
        __VUE_PROD_DEVTOOLS__: "false",
        __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: "false",
      });
      return definitions;
    });

    // 使用魔法注释来控制预加载行为
  },

  // 优化代码分割
  configureWebpack: () => {
    return process.env.VUE_APP_ENV == 'production' ? {
      optimization: {
        splitChunks: {
          chunks: "all", // 对所有模块进行分割
          maxSize: 244 * 1024, // 生成 chunk 的最大体积
          cacheGroups: {
            vendors: {
              name: "chunk-vendors", // 分包后的名称
              test: /[\\/]node_modules[\\/]/, // 匹配规则
              priority: 10, // 优先级
              chunks: "initial", // 只对入口文件处理
            },
            styles: {
              name: "styles",
              test: /\.(css|scss)$/,
              chunks: "all",
              enforce: true,
              priority: 30,
            },
          },
        },
        // 为 webpack 运行时代码创建单独的chunk
        runtimeChunk: {
          name: "runtime",
        },
        minimize: true,
      },
      plugins: [
        new CompressionPlugin({
          algorithm: "gzip", // 使用gzip压缩
          test: /\.(js|css|html|json|ico|svg)(\?.*)?$/i,
          threshold: 10240, // 只有大小大于该值的资源会被处理。单位是 bytes。默认值是 0。
          minRatio: 0.8, // 压缩率小于0.8才会压缩
          deleteOriginalAssets: false, // 删除压缩前原js文件 勿true（删除）
        }),
      ],
    } : {};
  },
});
