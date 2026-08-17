import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'

export const routes = [
  // La racine ne porte rien : elle mène à l'espace connecté, qui renverra vers le login
  // si le jeton n'est plus valable. Une page d'accueil publique existe déjà côté Thymeleaf.
  { path: '/', redirect: { name: 'home' } },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/home', name: 'home', component: HomeView, meta: { requiresAuth: true } },
]

/**
 * Garde d'authentification, exportée pour être testable hors du routeur applicatif.
 * Elle ne fait que lire l'état local : le serveur reste seul juge, et son refus est
 * traité au premier appel authentifié.
 */
export function authenticationGuard(to) {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated()) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.isAuthenticated()) {
    return { name: 'home' }
  }
  return true
}

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(authenticationGuard)

export default router
