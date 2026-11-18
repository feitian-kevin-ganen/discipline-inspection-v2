// pages/index/index.js —— 壳子首页，整页仅一个 web-view 加载 H5 考生端
Page({
  data: {
    // H5 考生端地址。
    // 注意：
    // 1. 正式发布时，H5 必须部署到 HTTPS 域名，并在小程序管理后台
    //    「开发管理 → 开发设置 → 业务域名」中配置该域名（需校验文件）。
    // 2. 开发阶段请在微信开发者工具勾选
    //    「详情 → 本地设置 → 不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」，
    //    否则 http 地址无法在 web-view 中加载。
    h5Url: 'http://10.110.10.160:6001/'
  },

  // H5 端通过 wx.miniProgram.postMessage 发来的消息（仅在小程序
  // 后退、组件销毁或分享等特定时机触发，这里转发到演示页查看）
  onWebViewMessage(e) {
    const list = (e.detail && e.detail.data) || [];
    console.log('[web-view] postMessage from H5:', list);
  },

  onWebViewLoad(e) {
    console.log('[web-view] loaded:', e.detail && e.detail.src);
  },

  onWebViewError(e) {
    console.error('[web-view] load error:', e.detail);
    wx.showToast({
      title: 'H5 加载失败，请检查地址与域名配置',
      icon: 'none'
    });
  }
});
