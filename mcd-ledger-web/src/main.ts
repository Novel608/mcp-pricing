import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import {
  Back,
  CircleCheck,
  Delete,
  Download,
  Files,
  FolderOpened,
  Goods,
  Money,
  Plus,
  Printer,
  Refresh,
  Select,
  ShoppingCart,
  Switch,
  User,
  WarningFilled,
} from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/index.css'
import './styles/plan.css'

const app = createApp(App)

app.use(ElementPlus)
app.use(router)

// 按需注册图标（模板中直接用 <el-icon><Download /></el-icon>）
for (const [name, component] of Object.entries({
  Back,
  CircleCheck,
  Delete,
  Download,
  Files,
  FolderOpened,
  Goods,
  Money,
  Plus,
  Printer,
  Refresh,
  Select,
  ShoppingCart,
  Switch,
  User,
  WarningFilled,
})) {
  app.component(name, component)
}

app.mount('#app')
