import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import axios from 'axios'
import router from './router'

const app = createApp(App)
app.use(ElementPlus)
app.use(router)
app.config.globalProperties.$axios = axios.create({
    baseURL:'http://localhost:8080/api/kb'
})
app.mount('#app')