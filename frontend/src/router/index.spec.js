import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { authenticationGuard, routes } from '@/router'
import { useAuthStore } from '@/stores/auth'

// Un routeur d'essai en historique mémoire : le garde est branché de la même façon que
// dans l'application, sans dépendre de l'URL du navigateur.
function createTestRouter() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  router.beforeEach(authenticationGuard)
  return router
}

function authenticate() {
  const auth = useAuthStore()
  auth.token = 'jeton-abc'
  auth.expiresAt = Date.now() + 3600_000
}

describe("garde d'authentification", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it("renvoie vers le login quand aucun jeton n'est détenu", async () => {
    const router = createTestRouter()

    await router.push('/home')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('renvoie vers le login quand le jeton a expiré', async () => {
    const auth = useAuthStore()
    auth.token = 'jeton-abc'
    auth.expiresAt = Date.now() - 1000
    const router = createTestRouter()

    await router.push('/home')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it("laisse atteindre l'espace connecté avec un jeton valable", async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/home')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it("renvoie du login vers l'espace connecté quand on est déjà connecté", async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it("dirige la racine vers l'espace connecté", async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('dirige la racine vers le login pour un visiteur anonyme', async () => {
    const router = createTestRouter()

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it("renvoie de l'inscription vers l'espace connecté quand on est déjà connecté", async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/register')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it("laisse un visiteur anonyme atteindre l'inscription", async () => {
    const router = createTestRouter()

    await router.push('/register')

    expect(router.currentRoute.value.name).toBe('register')
  })
})

describe('page de design system', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  // Vitest tourne avec import.meta.env.DEV à true : c'est la présence de la route qui est
  // vérifiée ici, pas son absence en production, que seul le build peut établir.
  it("est atteignable par un visiteur anonyme en développement, sans passer par l'espace connecté", async () => {
    const router = createTestRouter()

    await router.push('/design-system')

    expect(router.currentRoute.value.name).toBe('design-system')
  })

  it('ne renvoie pas un utilisateur connecté vers son espace', async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/design-system')

    expect(router.currentRoute.value.name).toBe('design-system')
  })
})
