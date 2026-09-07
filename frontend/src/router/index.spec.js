import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { authenticationGuard, routes } from '@/router'
import { useAuthStore } from '@/stores/auth'

// A test router on memory history: the guard is wired the same way as in the application,
// without depending on the browser URL.
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

describe('authentication guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('sends back to the login when no token is held', async () => {
    const router = createTestRouter()

    await router.push('/home')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('sends back to the login when the token has expired', async () => {
    const auth = useAuthStore()
    auth.token = 'jeton-abc'
    auth.expiresAt = Date.now() - 1000
    const router = createTestRouter()

    await router.push('/home')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('lets the signed-in space be reached with a valid token', async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/home')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('sends back from the login to the signed-in space when already signed in', async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('directs the root to the signed-in space', async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('directs the root to the login for an anonymous visitor', async () => {
    const router = createTestRouter()

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('sends back from the registration to the signed-in space when already signed in', async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/register')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('lets an anonymous visitor reach the registration', async () => {
    const router = createTestRouter()

    await router.push('/register')

    expect(router.currentRoute.value.name).toBe('register')
  })
})

describe('documents page', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('sends back to the login when no token is held', async () => {
    const router = createTestRouter()

    await router.push('/documents')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('lets the documents be reached with a valid token', async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/documents')

    expect(router.currentRoute.value.name).toBe('documents')
  })

  it('sends back to the login a document detail requested without a token', async () => {
    const router = createTestRouter()

    await router.push('/documents/doc-1')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it("opens a document's detail for a token bearer", async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/documents/doc-1')

    expect(router.currentRoute.value.name).toBe('document')
    expect(router.currentRoute.value.params.id).toBe('doc-1')
  })
})

describe('design system page', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  // Vitest runs with import.meta.env.DEV at true: what is checked here is the presence of
  // the route, not its absence in production, which only the build can establish.
  it('is reachable by an anonymous visitor in development, without the signed-in space', async () => {
    const router = createTestRouter()

    await router.push('/design-system')

    expect(router.currentRoute.value.name).toBe('design-system')
  })

  it('does not send a signed-in user back to their space', async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/design-system')

    expect(router.currentRoute.value.name).toBe('design-system')
  })
})

describe('conversation page', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('sends back to the login when no token is held', async () => {
    const router = createTestRouter()

    await router.push('/chat')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('lets the conversation be reached with a valid token', async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/chat')

    expect(router.currentRoute.value.name).toBe('chat')
  })
})
