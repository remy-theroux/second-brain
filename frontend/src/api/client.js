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
