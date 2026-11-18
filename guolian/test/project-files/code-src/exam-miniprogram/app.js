// 纪检监察人员在线考试系统 - 原生微信小程序考生端
// 复刻自 H5 考生端 code/exam-h5-web（Vue3 + Vant），纯原生 WXML/WXSS/JS 实现。
//
// 重要说明：
// 1. 开发期使用测试号（touristappid）+ IP 直连，project.config.json 已配置 urlCheck:false。
// 2. 正式发布前必须在微信公众平台「开发管理 -> 开发设置 -> 服务器域名」中配置
//    request 合法域名 与 downloadFile 合法域名（资料文件 wx.downloadFile / wx.openDocument 使用）。
// 3. app.json 中 tabBar 为纯文字占位，正式版需补充 iconPath / selectedIconPath 图标资源。
App({
  globalData: {
    // 后端 API 基地址（与 utils/request.js 中保持一致的唯一权威来源在 request.js）
    apiBase: 'http://10.110.10.160:8088/api/v1'
  },
  onLaunch() {
    // 登录态校验由各页面 onShow 中 utils/auth.js 的 ensureAuth 统一处理，
    // 这里不做强制跳转，保证从任意页面冷启动都能正确落地。
  }
})
