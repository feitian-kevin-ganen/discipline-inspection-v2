// pages/message/message.js
// 演示页：接收并展示 H5 通过 wx.miniProgram.postMessage 发来的消息。
// H5 端用法示例（需引入微信 JSSDK jweixin-1.x.x.js）：
//   wx.miniProgram.postMessage({ data: { action: 'examFinished', score: 90 } });
//   wx.miniProgram.navigateTo({ url: '/pages/message/message' });
// 注意：postMessage 的消息只会在小程序后退、web-view 销毁或分享时
// 通过 bindmessage 回调批量送达，此处用 navigateTo 携带参数做即时演示。
Page({
  data: {
    payloadText: '（暂无消息）'
  },

  onLoad(options) {
    if (options.payload) {
      let text = options.payload;
      try {
        text = JSON.stringify(JSON.parse(decodeURIComponent(options.payload)), null, 2);
      } catch (err) {
        // 非 JSON 时按原文展示
      }
      this.setData({ payloadText: text });
    }
  }
});
