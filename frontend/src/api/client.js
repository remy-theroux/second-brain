// Seul module du front qui connaisse HTTP : les URL, les en-têtes, les codes d'erreur et
// la forme des corps vivent ici, et nulle part ailleurs.

/** Le serveur a refusé le jeton : il est expiré, révoqué, ou ne désigne plus personne. */
export class UnauthorizedError extends Error {
  constructor() {
    super('Votre session a expiré.')
    this.name = 'UnauthorizedError'
  }
}

/**
 * Échange un email et un mot de passe contre un jeton d'accès.
 * Forme du `password grant` de RFC 6749 : corps encodé en formulaire.
 */
export async function requestToken(email, password) {
  const response = await fetch('/api/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'password', username: email, password }),
  })

  if (!response.ok) {
    // Le corps n'est pas garanti d'être du JSON (proxy en panne, 502 HTML…) : un parsing
    // qui échoue ne doit pas remplacer le message métier par une erreur de syntaxe.
    const payload = await response.json().catch(() => null)
    // error_description porte le message métier du serveur, affichable tel quel.
    throw new Error(payload?.error_description ?? 'La connexion a échoué.')
  }
  return response.json()
}

/** Lit le profil du porteur du jeton. C'est cet appel qui dit si la session tient encore. */
export async function fetchProfile(token) {
  const response = await fetch('/api/profile', {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (!response.ok) {
    throw new Error("Le profil n'a pas pu être chargé.")
  }
  return response.json()
}
