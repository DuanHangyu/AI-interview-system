/// <reference types="VUE/client" />

interface ImportMetaEnv {
  readonly VUE_APP_TITLE: string;
  readonly VUE_API_BASE_URL: string;
  readonly VUE_APP_BASE_API: string;
  readonly VUE_APP_ENV: string;
  // 其他自定义环境变量...
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
