// The only front module that knows HTTP: URLs, headers, error codes and the shape of
// bodies live here, and nowhere else.

import { readServerSentEvents } from '@/api/sse'

/** The server refused the token: it is expired, revoked, or no longer designates anyone. */
export class UnauthorizedError extends Error {
  constructor() {
    super('Votre session a expiré.')
    this.name = 'UnauthorizedError'
  }
}

/**
 * The uploaded content is already in the knowledge base. `existingDocumentId` designates the
 * document in place, so the screen can show it rather than leave it to be searched for.
 */
export class DuplicateDocumentError extends Error {
  constructor(message, existingDocumentId) {
    super(message)
    this.name = 'DuplicateDocumentError'
    this.existingDocumentId = existingDocumentId
  }
}

/** The input was refused field by field: `errors` maps a field name to its message. */
export class ValidationError extends Error {
  constructor(errors) {
    super('La saisie a été refusée.')
    this.name = 'ValidationError'
    this.errors = errors
  }
}

/**
 * Creates an account. Returns nothing on success: the server answers 201 without a body,
 * since nothing of the created account is readable until it is verified.
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

  // The body is not guaranteed to be JSON (proxy down, HTML 502…): a parse that fails
  // must not replace the business message with a syntax error.
  const payload = await response.json().catch(() => null)

  if (response.status === 422) {
    throw new ValidationError(payload?.errors ?? {})
  }
  throw new Error(payload?.message ?? "Votre compte n'a pas pu être créé.")
}

/**
 * Exchanges an email and a password for an access token.
 * Shape of the RFC 6749 `password grant`: form-encoded body.
 */
export async function requestToken(email, password) {
  const response = await fetch('/api/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'password', username: email, password }),
  })

  if (!response.ok) {
    // The body is not guaranteed to be JSON (proxy down, HTML 502…): a parse that fails
    // must not replace the business message with a syntax error.
    const payload = await response.json().catch(() => null)
    // error_description carries the server's business message, displayable as is.
    throw new Error(payload?.error_description ?? 'La connexion a échoué.')
  }
  return response.json()
}

/** Reads the profile of the token bearer. This call says whether the session still holds. */
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

/** Lists the token bearer's documents. An empty base returns an empty list, not an error. */
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
 * Reads a document and what has been extracted from it. The body carries the typology (`type`),
 * which says what shape `extraction` has — absent as long as nothing has been extracted.
 */
export async function fetchDocument(token, id) {
  const response = await fetch(`/api/documents/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (response.ok) {
    return response.json()
  }

  // The body is not guaranteed to be JSON (proxy down, HTML 502…): a parse that fails
  // must not replace the business message with a syntax error.
  const payload = await response.json().catch(() => null)
  // The 404 carries its own message, displayable as is.
  throw new Error(payload?.message ?? "Ce document n'a pas pu être chargé.")
}

/**
 * Reads the original file of a document, as it was uploaded. Returns a `Blob`: handing it to
 * the browser is a screen matter, this module only knows the call.
 */
export async function fetchDocumentContent(token, id) {
  const response = await fetch(`/api/documents/${id}/content`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (response.ok) {
    return response.blob()
  }

  // The body is not guaranteed to be JSON (proxy down, HTML 502…): a parse that fails
  // must not replace the business message with a syntax error.
  const payload = await response.json().catch(() => null)
  // The two 404 and the 503 each carry their message, displayable as is.
  throw new Error(payload?.message ?? "Le fichier n'a pas pu être téléchargé.")
}

/**
 * Uploads a document. Returns nothing on success: the server answers 201 without a body, and
 * it is the list that gives the complete state of the base.
 *
 * No `Content-Type` is set: the browser writes it itself with the multipart boundary, which
 * the server needs to know to split the body.
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

  // The body is not guaranteed to be JSON (proxy down, HTML 502…): a parse that fails
  // must not replace the business message with a syntax error.
  const payload = await response.json().catch(() => null)

  if (response.status === 409) {
    throw new DuplicateDocumentError(payload?.message, payload?.existingDocumentId)
  }
  if (response.status === 422) {
    throw new ValidationError(payload?.errors ?? {})
  }
  // 415 (format) and 413 (size) each carry their message, displayable as is.
  throw new Error(payload?.message ?? "Le document n'a pas pu être déposé.")
}

/** Removes a document. Returns nothing: the server answers 204. */
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

/**
 * Asks the agent a question and consumes its stream, resolving on the verdict once the
 * conversation is closed.
 *
 * `POST` + SSE cannot be read with `EventSource`, which only does `GET`: the body is read by
 * hand. `signal` aborts the request, and that abort is what stops generation server-side.
 */
export async function askAgent(token, question, { onToken, onSources, signal }) {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ question }),
    signal,
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (!response.ok) {
    // The body is not guaranteed to be JSON (proxy down, HTML 502…): a parse that fails
    // must not replace the business message with a syntax error.
    const payload = await response.json().catch(() => null)

    if (response.status === 422) {
      throw new ValidationError(payload?.errors ?? {})
    }
    throw new Error(payload?.message ?? "La conversation n'a pas pu démarrer.")
  }

  let verdict = null

  for await (const { event, data } of readServerSentEvents(response.body)) {
    if (event === 'token') {
      onToken(data)
    } else if (event === 'sources') {
      onSources(JSON.parse(data))
    } else if (event === 'done') {
      verdict = JSON.parse(data).verdict
    } else if (event === 'error') {
      // `error` comes INSTEAD of `sources` and `done`: nothing else will follow.
      throw new Error(JSON.parse(data).message)
    }
  }

  // Loose on purpose: a `done` payload carrying no `verdict` field leaves it undefined.
  if (verdict == null) {
    throw new Error("La conversation s'est interrompue avant la fin de la réponse.")
  }
  return verdict
}

/**
 * Reads the refusal of a Drive route: the server's message when there is one, the caller's
 * own message otherwise. The body is not guaranteed to be JSON (proxy down, HTML 502…), and a
 * parse that fails must not replace the business message with a syntax error.
 */
async function driveRefusal(response, fallback) {
  const payload = await response.json().catch(() => null)
  return new Error(payload?.message ?? fallback)
}

/**
 * Opens a Drive authorization and returns the Google consent URL to send the browser to.
 * The server answers 201 with that single URL: it is what the screen needs, not the envelope.
 */
export async function startDriveAuthorization(token) {
  const response = await fetch('/api/drive/authorizations', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (!response.ok) {
    throw await driveRefusal(response, "La connexion à Google Drive n'a pas pu démarrer.")
  }
  return (await response.json()).authorizationUrl
}

/**
 * Reads the connected Drive account, or `null` when there is none. The 404 is not a failure:
 * having connected no Drive is the ordinary state of an account, and showing an error message
 * there would greet every new user with one.
 */
export async function fetchDriveConnection(token) {
  const response = await fetch('/api/drive/connection', {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error("La connexion Google Drive n'a pas pu être lue.")
  }
  return response.json()
}

/** Disconnects the Drive account. Returns nothing: the server answers 204. */
export async function disconnectDrive(token) {
  const response = await fetch('/api/drive/connection', {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.ok) {
    return
  }
  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  // The 404 carries its own message, displayable as is.
  throw await driveRefusal(response, "Le compte Google Drive n'a pas pu être déconnecté.")
}

/**
 * Lists the Drive folders directly under `parentId`, or under the root when it is omitted.
 * One level at a time: the whole tree is never walked.
 */
export async function browseDriveFolders(token, parentId = '') {
  const query = parentId ? `?parent=${encodeURIComponent(parentId)}` : ''
  const response = await fetch(`/api/drive/folders${query}`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (!response.ok) {
    // The 409 (no Drive connected, or an authorization that no longer holds) and the 503
    // (Google unreachable) each carry their message, displayable as is.
    throw await driveRefusal(response, "Vos dossiers Google Drive n'ont pas pu être parcourus.")
  }
  return response.json()
}

/** Lists the watched folders, with the outcome of their last import. */
export async function listWatchedFolders(token) {
  const response = await fetch('/api/drive/watched-folders', {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (!response.ok) {
    throw new Error("La liste des dossiers surveillés n'a pas pu être chargée.")
  }
  return response.json()
}

/** Watches a Drive folder. Returns nothing: the server answers 201 without a body. */
export async function watchDriveFolder(token, folderId) {
  const response = await fetch('/api/drive/watched-folders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ folderId }),
  })

  if (response.ok) {
    return
  }
  if (response.status === 401) {
    throw new UnauthorizedError()
  }

  // The body is not guaranteed to be JSON (proxy down, HTML 502…): a parse that fails
  // must not replace the business message with a syntax error.
  const payload = await response.json().catch(() => null)

  if (response.status === 422) {
    throw new ValidationError(payload?.errors ?? {})
  }
  // The 409 (already covered, or no Drive connected), the 404 (unknown folder) and the 503
  // each carry their message, displayable as is.
  throw new Error(payload?.message ?? "Ce dossier n'a pas pu être surveillé.")
}

/** Stops watching a folder. Returns nothing: the server answers 204. */
export async function unwatchDriveFolder(token, id) {
  const response = await fetch(`/api/drive/watched-folders/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.ok) {
    return
  }
  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  throw await driveRefusal(response, "Ce dossier n'a pas pu être retiré de la surveillance.")
}

/**
 * Asks for a watched folder to be imported. Returns nothing: the server answers 202, the walk
 * not having even started when the answer leaves.
 */
export async function importWatchedFolder(token, id) {
  const response = await fetch(`/api/drive/watched-folders/${id}/import`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.ok) {
    return
  }
  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  throw await driveRefusal(response, "L'import de ce dossier n'a pas pu être demandé.")
}
