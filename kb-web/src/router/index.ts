import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('@/layout/Index.vue'),
      redirect: '/java-kb',
      children: [
        {
          path: 'java-kb',
          name: 'JavaKb',
          component: () => import('@/views/JavaKb.vue'),
          meta: { title: 'Java知识库' },
        },
        {
          path: 'home',
          name: 'Home',
          component: () => import('@/views/Home.vue'),
          meta: { title: '首页' },
        },
      ],
    },
  ],
})

export default router
