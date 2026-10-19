<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">知识库管理系统</div>
      <el-menu
          :default-active="activeMenu"
          router
          class="menu"
      >
        <el-menu-item index="/home">
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/java-kb">
          <span>Java知识库</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="header-title">{{ currentTitle }}</span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const activeMenu = computed(() => route.path)

const currentTitle = computed(() => route.meta?.title || '知识库管理系统')
</script>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background-color: #001529;
  color: #fff;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 18px;
  font-weight: bold;
  color: #fff;
  background-color: #002140;
}

.menu {
  border-right: none;
  background-color: #001529;
}

.menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.65);
}

.menu :deep(.el-menu-item:hover) {
  background-color: #001529;
  color: #fff;
}

.menu :deep(.el-menu-item.is-active) {
  background-color: #1890ff;
  color: #fff;
}

.header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.main {
  background-color: #f0f2f5;
}
</style>
