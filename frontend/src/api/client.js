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
 * Le contenu déposé est déjà dans la base de connaissance. `existingDocumentId` désigne le
 * document en place, pour que l'écran puisse le montrer plutôt que laisser chercher.
 */
export class DuplicateDocumentError extends Error {
  constructor(message, existingDocumentId) {
    super(message)
    this.name = 'DuplicateDocumentError'
    this.existingDocumentId = existingDocumentId
  }
}

/** La saisie a été refusée champ par champ : `errors` associe un nom de champ à son message. */
export class ValidationError extends Error {
  constructor(errors) {
    super('La saisie a été refusée.')
    this.name = 'ValidationError'
    this.errors = errors
  }
}

/**
 * Crée un compte. Ne rend rien en cas de succès : le serveur répond 201 sans corps,
 * puisque rien du compte créé n'est lisible tant qu'il n'est pas vérifié.
 */
export async function register(email, password) {
  const response = await fetch('/api/registrations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })

  if (response.ok) {
    return
  }

  // Le corps n'est pas garanti d'être du JSON (proxy en panne, 502 HTML…) : un parsing
  // qui échoue ne doit pas remplacer le message métier par une erreur de syntaxe.
  const payload = await response.json().catch(() => null)

  if (response.status === 422) {
    throw new ValidationError(payload?.errors ?? {})
  }
  throw new Error(payload?.message ?? "Votre compte n'a pas pu être créé.")
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

/** Liste les documents du porteur du jeton. Une base vide rend une liste vide, pas une erreur. */
export async function listDocuments(token) {
  const response = await fetch('/api/documents', {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (!response.ok) {
    throw new Error("La liste des documents n'a pas pu être chargée.")
  }
  return response.json()
}

/**
 * Dépose un document. Ne rend rien en cas de succès : le serveur répond 201 sans corps, et
 * c'est la liste qui donne l'état complet de la base.
 *
 * Aucun `Content-Type` n'est posé : le navigateur l'écrit lui-même avec le boundary du
 * multipart, que le serveur a besoin de connaître pour découper le corps.
 */
export async function uploadDocument(token, file) {
  const body = new FormData()
  body.append('file', file)

  const response = await fetch('/api/documents', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body,
  })

  if (response.ok) {
    return
  }
  if (response.status === 401) {
    throw new UnauthorizedError()
  }

  // Le corps n'est pas garanti d'être du JSON (proxy en panne, 502 HTML…) : un parsing
  // qui échoue ne doit pas remplacer le message métier par une erreur de syntaxe.
  const payload = await response.json().catch(() => null)

  if (response.status === 409) {
    throw new DuplicateDocumentError(payload?.message, payload?.existingDocumentId)
  }
  if (response.status === 422) {
    throw new ValidationError(payload?.errors ?? {})
  }
  // 415 (format) et 413 (taille) portent chacun leur message, affichable tel quel.
  throw new Error(payload?.message ?? "Le document n'a pas pu être déposé.")
}

/** Retire un document. Ne rend rien : le serveur répond 204. */
export async function deleteDocument(token, id) {
  const response = await fetch(`/api/documents/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.ok) {
    return
  }
  if (response.status === 401) {
    throw new UnauthorizedError()
  }

  const payload = await response.json().catch(() => null)
  throw new Error(payload?.message ?? "Le document n'a pas pu être supprimé.")
}
