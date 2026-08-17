import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

function jsonResponse(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body }
}

function stubFetch(status, body) {
  const fetchStub = vi.fn().mockResolvedValue(jsonResponse(status, body))
  vi.stubGlobal('fetch', fetchStub)
  return fetchStub
}

// Simule un corps d'erreur non JSON (page HTML d'un proxy en panne, par exemple) :
// `response.json()` échoue avec une `SyntaxError`, comme le ferait le vrai `fetch`.
function stubFetchWithUnparsableBody(status) {
  const fetchStub = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      throw new SyntaxError("Unexpected token '<', \"<html>...\" is not valid JSON")
    },
  })
  vi.stubGlobal('fetch', fetchStub)
  return fetchStub
}

describe("store d'authentification", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  // Filet de sécurité : si l'assertion du test à minuteurs simulés échoue avant d'atteindre
  // `vi.useRealTimers()`, les minuteurs simulés ne doivent pas fuir vers les tests suivants.
  afterEach(() => {
    vi.useRealTimers()
  })

  it('mémorise le jeton et son expiration après une connexion réussie', async () => {
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()

    await auth.login('alice@exemple.fr', 'chevalpile42')

    expect(auth.token).toBe('jeton-abc')
    expect(auth.isAuthenticated()).toBe(true)
    expect(localStorage.getItem('second-brain.access-token')).toBe('jeton-abc')
  })

  it("envoie l'échange au format attendu par le serveur", async () => {
    const fetchStub = stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()

    await auth.login('alice@exemple.fr', 'chevalpile42')

    const [url, options] = fetchStub.mock.calls[0]
    expect(url).toBe('/api/token')
    expect(options.method).toBe('POST')
    expect(options.headers['Content-Type']).toBe('application/x-www-form-urlencoded')
    expect(options.body.toString()).toContain('grant_type=password')
    expect(options.body.toString()).toContain('username=alice%40exemple.fr')
  })

  it('propage le message du serveur quand la connexion échoue', async () => {
    stubFetch(400, { error: 'invalid_grant', error_description: 'Email ou mot de passe incorrect.' })
    const auth = useAuthStore()

    await expect(auth.login('alice@exemple.fr', 'faux')).rejects.toThrow('Email ou mot de passe incorrect.')
    expect(auth.isAuthenticated()).toBe(false)
  })

  it('propage le message français par défaut quand le corps d\'échec n\'est pas du JSON', async () => {
    stubFetchWithUnparsableBody(502)
    const auth = useAuthStore()

    await expect(auth.login('alice@exemple.fr', 'chevalpile42')).rejects.toThrow('La connexion a échoué.')
    expect(auth.isAuthenticated()).toBe(false)
  })

  it("n'est plus authentifié quand le jeton a expiré", async () => {
    vi.useFakeTimers()
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()

    await auth.login('alice@exemple.fr', 'chevalpile42')
    vi.advanceTimersByTime(3601 * 1000)

    // isAuthenticated doit être une fonction : un `computed` renverrait la valeur mise en
    // cache, ses dépendances réactives n'ayant pas bougé — seule l'horloge a avancé.
    expect(auth.isAuthenticated()).toBe(false)
  })

  it('vide le stockage local à la déconnexion', async () => {
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()
    await auth.login('alice@exemple.fr', 'chevalpile42')

    auth.logout()

    expect(auth.token).toBeNull()
    expect(auth.isAuthenticated()).toBe(false)
    expect(localStorage.getItem('second-brain.access-token')).toBeNull()
  })

  it('relit le jeton du stockage local au démarrage', () => {
    localStorage.setItem('second-brain.access-token', 'jeton-abc')
    localStorage.setItem('second-brain.access-token-expiration', String(Date.now() + 3600_000))

    const auth = useAuthStore()

    expect(auth.isAuthenticated()).toBe(true)
  })

  it('déconnecte quand le profil répond 401', async () => {
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()
    await auth.login('alice@exemple.fr', 'chevalpile42')
    stubFetch(401, {})

    await expect(auth.loadProfile()).rejects.toThrow()
    expect(auth.isAuthenticated()).toBe(false)
  })

  it('charge le profil du compte connecté', async () => {
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()
    await auth.login('alice@exemple.fr', 'chevalpile42')
    const fetchStub = stubFetch(200, { id: 'un-uuid', email: 'alice@exemple.fr', verified: true })

    await auth.loadProfile()

    expect(auth.profile.email).toBe('alice@exemple.fr')
    expect(fetchStub.mock.calls[0][1].headers.Authorization).toBe('Bearer jeton-abc')
  })
})
