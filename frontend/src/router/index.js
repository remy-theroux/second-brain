import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import HomeView from '@/views/HomeView.vue'
import DocumentsView from '@/views/DocumentsView.vue'
import DocumentDetailView from '@/views/DocumentDetailView.vue'
import ChatView from '@/views/ChatView.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import DesignSystemView from '@/views/DesignSystemView.vue'

export const routes = [
  // The root carries nothing: it leads to the signed-in space, which will send back to the
  // login if the token is no longer valid. There is no public home page any more.
  { path: '/', redirect: { name: 'home' } },
  { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
  { path: '/register', name: 'register', component: RegisterView, meta: { guestOnly: true } },
  { path: '/home', name: 'home', component: HomeView, meta: { requiresAuth: true } },
  { path: '/documents', name: 'documents', component: DocumentsView, meta: { requiresAuth: true } },
  // The detail is addressable: an extracted text can be read again, shared by its URL and
  // survives an F5. A modal over the list would have offered none of that.
  {
    path: '/documents/:id',
    name: 'document',
    component: DocumentDetailView,
    meta: { requiresAuth: true },
  },
  { path: '/chat', name: 'chat', component: ChatView, meta: { requiresAuth: true } },
  // Catalogue of the tokens and shared components, for the human pass in a browser.
  // Development only: the conditional spread removes the route AND the view from the
  // production bundle, rather than a guard that would leave the code shipped. Neither
  // `guestOnly` nor `requiresAuth`: the page is looked at signed in or not.
  ...(import.meta.env.DEV
    ? [
        {
          path: '/design-system',
          name: 'design-system',
          component: DesignSystemView,
          meta: { layout: 'bare' },
        },
      ]
    : []),
]

/**
 * Authentication guard, exported to be testable outside the application router.
 * It only reads the local state: the server remains the sole judge, and its refusal is
 * handled on the first authenticated call.
 */
export function authenticationGuard(to) {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated()) {
    return { name: 'login' }
  }
  // `guestOnly` rather than a comparison on the route name: a page reserved for anonymous
  // visitors declares itself, it is not enumerated in the guard.
  if (to.meta.guestOnly && auth.isAuthenticated()) {
    return { name: 'home' }
  }
  return true
}

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(authenticationGuard)

export default router
