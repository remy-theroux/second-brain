import { ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchProfile, requestToken, UnauthorizedError } from '@/api/client'

// The token survives a page refresh — otherwise "staying signed in" would mean nothing.
// The price is known: an XSS flaw would give away the token. The countermeasure
// (httpOnly cookie + refresh token, hence CSRF to bring back) is a ticket of its own.
const TOKEN_KEY = 'second-brain.access-token'
const EXPIRATION_KEY = 'second-brain.access-token-expiration'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY))
  const expiresAt = ref(Number(localStorage.getItem(EXPIRATION_KEY)) || 0)
  const profile = ref(null)

  /**
   * A function and not a `computed`: the result depends on the clock, which is not a
   * reactive dependency. A `computed` would stay `true` after expiration until some
   * other state changed — and the route guard would let it through.
   */
  function isAuthenticated() {
    return token.value !== null && expiresAt.value > Date.now()
  }

  async function login(email, password) {
    const payload = await requestToken(email, password)
    // An expired session does not go through `logout()`: the previous account's profile is
    // still there, and it would show while the new one loads — indefinitely if that
    // load fails.
    profile.value = null
    token.value = payload.access_token
    expiresAt.value = Date.now() + payload.expires_in * 1000
    localStorage.setItem(TOKEN_KEY, token.value)
    localStorage.setItem(EXPIRATION_KEY, String(expiresAt.value))
  }

  function logout() {
    token.value = null
    expiresAt.value = 0
    profile.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(EXPIRATION_KEY)
  }

  async function loadProfile() {
    try {
      profile.value = await fetchProfile(token.value)
    } catch (error) {
      // A refusal from the server overrides what the browser thought it knew.
      if (error instanceof UnauthorizedError) {
        logout()
      }
      throw error
    }
  }

  return { token, expiresAt, profile, isAuthenticated, login, logout, loadProfile }
})
