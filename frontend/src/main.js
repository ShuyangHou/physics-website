import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import {
  ArrowDown,
  Avatar,
  Calendar,
  CircleCheckFilled,
  Clock,
  Collection,
  Delete,
  Document,
  DocumentRemove,
  Download,
  Edit,
  Expand,
  Fold,
  Grid,
  House,
  InfoFilled,
  Loading,
  Lock,
  Plus,
  Printer,
  Rank,
  Refresh,
  RefreshRight,
  RemoveFilled,
  Search,
  Select,
  Setting,
  Star,
  Trophy,
  Unlock,
  Upload,
  UploadFilled,
  User,
  UserFilled,
  View,
  Warning
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/index.scss'
import './styles/responsive.css'
import { permission, admin, teacher, student } from './directives/permission'

const app = createApp(App)

const icons = {
  ArrowDown,
  Avatar,
  Calendar,
  CircleCheckFilled,
  Clock,
  Collection,
  Delete,
  Document,
  DocumentRemove,
  Download,
  Edit,
  Expand,
  Fold,
  Grid,
  House,
  InfoFilled,
  Loading,
  Lock,
  Plus,
  Printer,
  Rank,
  Refresh,
  RefreshRight,
  RemoveFilled,
  Search,
  Select,
  Setting,
  Star,
  Trophy,
  Unlock,
  Upload,
  UploadFilled,
  User,
  UserFilled,
  View,
  Warning
}

for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

app.directive('permission', permission)
app.directive('admin', admin)
app.directive('teacher', teacher)
app.directive('student', student)

app.use(createPinia())
app.use(router)

app.mount('#app')
