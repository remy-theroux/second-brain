import { ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchProfile, requestToken, UnauthorizedError } from '@/api/client'

// Le jeton survit à un rafraîchissement de page — sinon « maintenir une connexion » ne
// veut rien dire. Le prix est connu : une faille XSS donnerait le jeton. La parade
// (cookie httpOnly + jeton de rafraîchissement, donc CSRF à réactiver) est un ticket
// à part entière.
const TOKEN_KEY = 'second-brain.access-token'
const EXPIRATION_KEY = 'second-brain.access-token-expiration'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY))
  const expiresAt = ref(Number(localStorage.getItem(EXPIRATION_KEY)) || 0)
  const profile = ref(null)

  /**
   * Fonction et non `computed` : le résultat dépend de l'horloge, qui n'est pas une
   * dépendance réactive. Un `computed` resterait à `true` après expiration jusqu'à ce
   * qu'un autre état change — et le garde de route laisserait passer.
   */
  function isAuthenticated() {
    return token.value !== null && expiresAt.value > Date.now()
  }

  async function login(email, password) {
    const payload = await requestToken(email, password)
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
      // Un refus du serveur fait autorité sur ce que le navigateur croyait savoir.
      if (error instanceof UnauthorizedError) {
        logout()
      }
      throw error
    }
  }

  return { token, expiresAt, profile, isAuthenticated, login, logout, loadProfile }
})
