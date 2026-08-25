import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import HomeView from '@/views/HomeView.vue'
import DocumentsView from '@/views/DocumentsView.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import DesignSystemView from '@/views/DesignSystemView.vue'

export const routes = [
  // La racine ne porte rien : elle mène à l'espace connecté, qui renverra vers le login
  // si le jeton n'est plus valable. Il n'existe plus de page publique d'accueil.
  { path: '/', redirect: { name: 'home' } },
  { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
  { path: '/register', name: 'register', component: RegisterView, meta: { guestOnly: true } },
  { path: '/home', name: 'home', component: HomeView, meta: { requiresAuth: true } },
  { path: '/documents', name: 'documents', component: DocumentsView, meta: { requiresAuth: true } },
  // Catalogue des tokens et des composants partagés, pour le passage humain dans un
  // navigateur. Développement seulement : le spread conditionnel retire la route ET la vue
  // du bundle de production, plutôt qu'un garde qui laisserait le code embarqué. Ni
  // `guestOnly` ni `requiresAuth` : la page se regarde connecté ou non.
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
 * Garde d'authentification, exportée pour être testable hors du routeur applicatif.
 * Elle ne fait que lire l'état local : le serveur reste seul juge, et son refus est
 * traité au premier appel authentifié.
 */
export function authenticationGuard(to) {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated()) {
    return { name: 'login' }
  }
  // `guestOnly` plutôt qu'une comparaison sur le nom de la route : une page réservée aux
  // visiteurs anonymes se déclare, elle ne s'énumère pas dans le garde.
  if (to.meta.guestOnly && auth.isAuthenticated()) {
    return { name: 'home' }
  }
  return true
}

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(authenticationGuard)

export default router
