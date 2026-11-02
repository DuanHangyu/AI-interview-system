// 通过浏览器打印导出 PDF：矢量文字、可复制，中文字体由系统提供。打印期间把文档标题改成文件名。
export function printReport(fileName: string) {
  const original = document.title;
  document.title = fileName.replace(/[\\/:*?"<>|]/g, "_");
  const restore = () => {
    document.title = original;
    window.removeEventListener("afterprint", restore);
  };
  window.addEventListener("afterprint", restore);
  window.print();
}
