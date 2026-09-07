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

// Simulates a non-JSON error body (the HTML page of a proxy that is down, for instance):
// `response.json()` fails with a `SyntaxError`, as the real `fetch` would.
function stubFetchWithUnparsableBody(status) {
  const fetchStub = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      throw new SyntaxError('Unexpected token \'<\', "<html>..." is not valid JSON')
    },
  })
  vi.stubGlobal('fetch', fetchStub)
  return fetchStub
}

describe('authentication store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  // Safety net: if the assertion of the fake-timers test fails before reaching
  // `vi.useRealTimers()`, the fake timers must not leak into the following tests.
  afterEach(() => {
    vi.useRealTimers()
  })

  it('remembers the token and its expiration after a successful sign-in', async () => {
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()

    await auth.login('alice@exemple.fr', 'chevalpile42')

    expect(auth.token).toBe('jeton-abc')
    expect(auth.isAuthenticated()).toBe(true)
    expect(localStorage.getItem('second-brain.access-token')).toBe('jeton-abc')
  })

  it("forgets the previous account's profile as soon as the next sign-in happens", async () => {
    // An expired session does not go through `logout()`: the guard sends back to `/login`
    // leaving the profile in place. Without an explicit forget, `HomeView` shows the
    // previous account's address while the new one loads — and forever if that load fails.
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()
    await auth.login('alice@exemple.fr', 'chevalpile42')
    stubFetch(200, { id: 'un-uuid', email: 'alice@exemple.fr', verified: true })
    await auth.loadProfile()

    stubFetch(200, { access_token: 'jeton-xyz', token_type: 'Bearer', expires_in: 3600 })
    await auth.login('bob@exemple.fr', 'chevalpile43')

    expect(auth.profile).toBeNull()
  })

  it('sends the exchange in the format the server expects', async () => {
    const fetchStub = stubFetch(200, {
      access_token: 'jeton-abc',
      token_type: 'Bearer',
      expires_in: 3600,
    })
    const auth = useAuthStore()

    await auth.login('alice@exemple.fr', 'chevalpile42')

    const [url, options] = fetchStub.mock.calls[0]
    expect(url).toBe('/api/token')
    expect(options.method).toBe('POST')
    expect(options.headers['Content-Type']).toBe('application/x-www-form-urlencoded')
    expect(options.body.toString()).toContain('grant_type=password')
    expect(options.body.toString()).toContain('username=alice%40exemple.fr')
  })

  it("propagates the server's message when the sign-in fails", async () => {
    stubFetch(400, {
      error: 'invalid_grant',
      error_description: 'Email ou mot de passe incorrect.',
    })
    const auth = useAuthStore()

    await expect(auth.login('alice@exemple.fr', 'faux')).rejects.toThrow(
      'Email ou mot de passe incorrect.',
    )
    expect(auth.isAuthenticated()).toBe(false)
  })

  it('propagates the default French message when the failure body is not JSON', async () => {
    stubFetchWithUnparsableBody(502)
    const auth = useAuthStore()

    await expect(auth.login('alice@exemple.fr', 'chevalpile42')).rejects.toThrow(
      'La connexion a échoué.',
    )
    expect(auth.isAuthenticated()).toBe(false)
  })

  it('is no longer authenticated when the token has expired', async () => {
    vi.useFakeTimers()
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()

    await auth.login('alice@exemple.fr', 'chevalpile42')
    vi.advanceTimersByTime(3601 * 1000)

    // isAuthenticated must be a function: a `computed` would return the cached value, its
    // reactive dependencies not having moved — only the clock has advanced.
    expect(auth.isAuthenticated()).toBe(false)
  })

  it('clears the local storage on sign-out', async () => {
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()
    await auth.login('alice@exemple.fr', 'chevalpile42')

    auth.logout()

    expect(auth.token).toBeNull()
    expect(auth.isAuthenticated()).toBe(false)
    expect(localStorage.getItem('second-brain.access-token')).toBeNull()
  })

  it('reads the token back from the local storage at startup', () => {
    localStorage.setItem('second-brain.access-token', 'jeton-abc')
    localStorage.setItem('second-brain.access-token-expiration', String(Date.now() + 3600_000))

    const auth = useAuthStore()

    expect(auth.isAuthenticated()).toBe(true)
  })

  it('signs out when the profile answers 401', async () => {
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()
    await auth.login('alice@exemple.fr', 'chevalpile42')
    stubFetch(401, {})

    await expect(auth.loadProfile()).rejects.toThrow()
    expect(auth.isAuthenticated()).toBe(false)
  })

  it("loads the signed-in account's profile", async () => {
    stubFetch(200, { access_token: 'jeton-abc', token_type: 'Bearer', expires_in: 3600 })
    const auth = useAuthStore()
    await auth.login('alice@exemple.fr', 'chevalpile42')
    const fetchStub = stubFetch(200, { id: 'un-uuid', email: 'alice@exemple.fr', verified: true })

    await auth.loadProfile()

    expect(auth.profile.email).toBe('alice@exemple.fr')
    expect(fetchStub.mock.calls[0][1].headers.Authorization).toBe('Bearer jeton-abc')
  })
})
