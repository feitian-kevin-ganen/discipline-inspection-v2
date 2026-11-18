<template>
  <div class="page page-has-tabbar">
    <router-view />
    <van-tabbar route fixed placeholder safe-area-inset-bottom>
      <van-tabbar-item to="/learn" icon="orders-o">学习</van-tabbar-item>
      <van-tabbar-item to="/exam" icon="edit">测试</van-tabbar-item>
      <van-tabbar-item to="/practice" icon="aim">练习</van-tabbar-item>
      <van-tabbar-item to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
</script>
