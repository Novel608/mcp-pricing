import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/ledger/1' },
  {
    path: '/ledger/:id',
    name: 'ledger-workbench',
    component: () => import('@/views/ledger/LedgerWorkbench.vue'),
    props: (route) => ({ ledgerId: Number(route.params.id) || 1 }),
  },
  {
    path: '/ledger/:id/print',
    name: 'ledger-print',
    component: () => import('@/views/ledger/PrintView.vue'),
    props: (route) => ({ ledgerId: Number(route.params.id) || 1 }),
  },
  { path: '/plan', redirect: '/plan/1' },
  {
    path: '/plan/:id',
    name: 'plan-workbench',
    component: () => import('@/views/plan/PlanWorkbench.vue'),
    props: (route) => ({ planId: Number(route.params.id) || 1 }),
  },
  { path: '/:pathMatch(.*)*', redirect: '/ledger/1' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

export default router
