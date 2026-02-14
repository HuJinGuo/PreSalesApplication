import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import MainLayout from '@/views/MainLayout.vue'
import ProjectListView from '@/views/ProjectListView.vue'
import ProjectDetailView from '@/views/ProjectDetailView.vue'
import DocumentEditorView from '@/views/DocumentEditorView.vue'
import AssetLibraryView from '@/views/AssetLibraryView.vue'
import ReviewCenterView from '@/views/ReviewCenterView.vue'
import ExportHistoryView from '@/views/ExportHistoryView.vue'
import KnowledgeBaseView from '@/views/KnowledgeBaseView.vue'
import DomainLexiconView from '@/views/DomainLexiconView.vue'
import KnowledgeGraphView from '@/views/KnowledgeGraphView.vue'
import ExamCenterView from '@/views/ExamCenterView.vue'
import PublicExamLinkView from '@/views/PublicExamLinkView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/exam-link/:token', component: PublicExamLinkView },
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', redirect: '/projects' },
        { path: '/projects', component: ProjectListView },
        { path: '/projects/:id', component: ProjectDetailView },
        { path: '/documents/:id/edit', component: DocumentEditorView },
        { path: '/assets', component: AssetLibraryView },
        { path: '/knowledge', component: KnowledgeBaseView },
        { path: '/domain-lexicons', component: DomainLexiconView },
        { path: '/knowledge-graph', component: KnowledgeGraphView },
        { path: '/exams', component: ExamCenterView },
        { path: '/reviews', component: ReviewCenterView },
        { path: '/exports/:docId', component: ExportHistoryView }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !to.path.startsWith('/exam-link/') && !auth.token) {
    return '/login'
  }
  return true
})

export default router
