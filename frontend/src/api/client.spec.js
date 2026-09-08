import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  askAgent,
  browseDriveFolders,
  deleteDocument,
  disconnectDrive,
  DuplicateDocumentError,
  fetchDocument,
  fetchDocumentContent,
  fetchDriveConnection,
  importWatchedFolder,
  listDocuments,
  listWatchedFolders,
  register,
  startDriveAuthorization,
  UnauthorizedError,
  unwatchDriveFolder,
  uploadDocument,
  ValidationError,
  watchDriveFolder,
} from '@/api/client'

// Minimal response: only the status and the JSON body matter for this module.
function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }
}

// A binary response: only the status and the blob matter for this module.
function blobResponse(status, blob) {
  return {
    ok: status >= 200 && status < 300,
    status,
    blob: () => Promise.resolve(blob),
  }
}

describe('account creation', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts the input as JSON to the registrations route', async () => {
    fetch.mockResolvedValue(jsonResponse(201, null))

    await register('alice@example.com', 'chevalpile42')

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/api/registrations')
    expect(options.method).toBe('POST')
    expect(options.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(options.body)).toEqual({
      email: 'alice@example.com',
      password: 'chevalpile42',
    })
  })

  it('translates a 422 into per-field errors', async () => {
    fetch.mockResolvedValue(jsonResponse(422, { errors: { email: "L'email n'est pas valide." } }))

    await expect(register('pas-un-email', 'chevalpile42')).rejects.toThrow(ValidationError)

    try {
      await register('pas-un-email', 'chevalpile42')
    } catch (error) {
      expect(error.errors).toEqual({ email: "L'email n'est pas valide." })
    }
  })

  it('translates a 503 into a global message', async () => {
    fetch.mockResolvedValue(jsonResponse(503, { message: "L'email n'a pas pu être envoyé." }))

    await expect(register('alice@example.com', 'chevalpile42')).rejects.toThrow(
      "L'email n'a pas pu être envoyé.",
    )
  })

  it('does not replace the failure with a syntax error when the body is not JSON', async () => {
    // A proxy that is down returns HTML: the parse fails, but the user must read a useful
    // message, not "Unexpected token <".
    fetch.mockResolvedValue({
      ok: false,
      status: 502,
      json: () => Promise.reject(new SyntaxError('Unexpected token <')),
    })

    await expect(register('alice@example.com', 'chevalpile42')).rejects.toThrow(
      "Votre compte n'a pas pu être créé.",
    )
  })
})

describe('knowledge base', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('document list', () => {
    it('reads the list with the bearer token', async () => {
      const documents = [{ id: 'doc-1', filename: 'notes.md', status: 'PENDING' }]
      fetch.mockResolvedValue(jsonResponse(200, documents))

      const result = await listDocuments('jeton-abc')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(result).toEqual(documents)
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(listDocuments('jeton-perime')).rejects.toThrow(UnauthorizedError)
    })

    it('translates any other failure into a global message', async () => {
      fetch.mockResolvedValue(jsonResponse(500, null))

      await expect(listDocuments('jeton-abc')).rejects.toThrow(
        "La liste des documents n'a pas pu être chargée.",
      )
    })
  })

  describe('reading a document', () => {
    it('reads the document and its extraction with the bearer token', async () => {
      const expected = {
        id: 'doc-1',
        filename: 'notes.md',
        type: 'TEXTUAL',
        status: 'EXTRACTED',
        extraction: { extractedAt: '2026-08-26T10:00:00Z', characterCount: 120, blocks: [] },
      }
      fetch.mockResolvedValue(jsonResponse(200, expected))

      const document = await fetchDocument('jeton-abc', 'doc-1')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents/doc-1')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(document).toEqual(expected)
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(fetchDocument('jeton-perime', 'doc-1')).rejects.toThrow(UnauthorizedError)
    })

    it("returns the server's message for a document that cannot be found", async () => {
      fetch.mockResolvedValue(jsonResponse(404, { message: 'Ce document est introuvable.' }))

      await expect(fetchDocument('jeton-abc', 'doc-1')).rejects.toThrow(
        'Ce document est introuvable.',
      )
    })

    it('returns a default message when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new Error('pas du JSON')),
      })

      await expect(fetchDocument('jeton-abc', 'doc-1')).rejects.toThrow(
        "Ce document n'a pas pu être chargé.",
      )
    })
  })

  describe('uploading a document', () => {
    const file = new File(['# Notes'], 'notes.md', { type: 'text/markdown' })

    it('posts the file as multipart under the name "file", with the bearer token', async () => {
      fetch.mockResolvedValue(jsonResponse(201, null))

      await uploadDocument('jeton-abc', file)

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents')
      expect(options.method).toBe('POST')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(options.body).toBeInstanceOf(FormData)
      expect(options.body.get('file')).toBe(file)
      // The browser sets the multipart Content-Type itself with its boundary: setting it
      // by hand would strip the boundary, and the server could no longer split the body.
      expect(options.headers['Content-Type']).toBeUndefined()
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(uploadDocument('jeton-perime', file)).rejects.toThrow(UnauthorizedError)
    })

    it('translates a 409 into a duplicate designating the existing document', async () => {
      fetch.mockResolvedValue(
        jsonResponse(409, {
          message: 'Ce document est déjà présent.',
          existingDocumentId: 'doc-1',
        }),
      )

      await expect(uploadDocument('jeton-abc', file)).rejects.toThrow(DuplicateDocumentError)

      try {
        await uploadDocument('jeton-abc', file)
      } catch (error) {
        expect(error.message).toBe('Ce document est déjà présent.')
        expect(error.existingDocumentId).toBe('doc-1')
      }
    })

    it('translates a 422 into per-field errors', async () => {
      fetch.mockResolvedValue(
        jsonResponse(422, { errors: { file: 'Le fichier est obligatoire.' } }),
      )

      await expect(uploadDocument('jeton-abc', file)).rejects.toThrow(ValidationError)

      try {
        await uploadDocument('jeton-abc', file)
      } catch (error) {
        expect(error.errors).toEqual({ file: 'Le fichier est obligatoire.' })
      }
    })

    it("displays the server's message as is for a refused format", async () => {
      fetch.mockResolvedValue(
        jsonResponse(415, { message: 'Formats acceptés : .pdf, .md, .txt, .docx.' }),
      )

      await expect(uploadDocument('jeton-abc', file)).rejects.toThrow(
        'Formats acceptés : .pdf, .md, .txt, .docx.',
      )
    })

    it("displays the server's message as is for a file that is too large", async () => {
      fetch.mockResolvedValue(
        jsonResponse(413, { message: 'Ce fichier dépasse la taille maximale acceptée.' }),
      )

      await expect(uploadDocument('jeton-abc', file)).rejects.toThrow(
        'Ce fichier dépasse la taille maximale acceptée.',
      )
    })

    it('does not replace the failure with a syntax error when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(uploadDocument('jeton-abc', file)).rejects.toThrow(
        "Le document n'a pas pu être déposé.",
      )
    })
  })

  describe('deleting a document', () => {
    it('sends a DELETE on the document, with the bearer token', async () => {
      fetch.mockResolvedValue(jsonResponse(204, null))

      await deleteDocument('jeton-abc', 'doc-1')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents/doc-1')
      expect(options.method).toBe('DELETE')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(deleteDocument('jeton-perime', 'doc-1')).rejects.toThrow(UnauthorizedError)
    })

    it("displays the server's message as is for a document that cannot be found", async () => {
      fetch.mockResolvedValue(jsonResponse(404, { message: "Ce document n'existe pas." }))

      await expect(deleteDocument('jeton-abc', 'doc-1')).rejects.toThrow(
        "Ce document n'existe pas.",
      )
    })

    it('does not replace the failure with a syntax error when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(deleteDocument('jeton-abc', 'doc-1')).rejects.toThrow(
        "Le document n'a pas pu être supprimé.",
      )
    })
  })

  describe('downloading the original file', () => {
    it('reads the content route with the bearer token', async () => {
      const file = new Blob(['bonjour'], { type: 'text/plain' })
      fetch.mockResolvedValue(blobResponse(200, file))

      const result = await fetchDocumentContent('jeton-abc', 'doc-1')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents/doc-1/content')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(result).toBe(file)
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(blobResponse(401, null))

      await expect(fetchDocumentContent('jeton-perime', 'doc-1')).rejects.toThrow(UnauthorizedError)
    })

    it('surfaces the message of a vanished original', async () => {
      fetch.mockResolvedValue(
        jsonResponse(404, { message: "L'original de ce document n'est plus disponible." }),
      )

      await expect(fetchDocumentContent('jeton-abc', 'doc-1')).rejects.toThrow(
        "L'original de ce document n'est plus disponible.",
      )
    })

    it('falls back to its own message when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(fetchDocumentContent('jeton-abc', 'doc-1')).rejects.toThrow(
        "Le fichier n'a pas pu être téléchargé.",
      )
    })
  })
})

describe('conversation', () => {
  // Builds the response a streaming route returns: a status, and a body that hands the
  // given SSE text over as bytes.
  function streamResponse(status, text) {
    const encoder = new TextEncoder()
    return {
      ok: status >= 200 && status < 300,
      status,
      body: new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode(text))
          controller.close()
        },
      }),
    }
  }

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts the question as JSON, bearing the token', async () => {
    fetch.mockResolvedValue(
      streamResponse(200, 'event:done\ndata:{"verdict":"CONVERSATIONAL"}\n\n'),
    )

    await askAgent('jeton-abc', 'Quel est le délai ?', { onToken: () => {}, onSources: () => {} })

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/api/chat')
    expect(options.method).toBe('POST')
    expect(options.headers.Authorization).toBe('Bearer jeton-abc')
    expect(JSON.parse(options.body)).toEqual({ question: 'Quel est le délai ?' })
  })

  it('hands over each fragment, then the sources, then the verdict', async () => {
    fetch.mockResolvedValue(
      streamResponse(
        200,
        'event:token\ndata:Quatorze\n\n' +
          'event:token\ndata: jours [1].\n\n' +
          'event:sources\ndata:[{"number":1,"documentId":"doc-1","filename":"rapport.pdf","position":3,"heading":"Rétractation","text":"Le délai est de quatorze jours."}]\n\n' +
          'event:done\ndata:{"verdict":"GROUNDED"}\n\n',
      ),
    )
    const fragments = []
    let received = null

    const verdict = await askAgent('jeton-abc', 'Quel est le délai ?', {
      onToken: (fragment) => fragments.push(fragment),
      onSources: (sources) => {
        received = sources
      },
    })

    expect(fragments.join('')).toBe('Quatorze jours [1].')
    expect(received).toHaveLength(1)
    expect(received[0].filename).toBe('rapport.pdf')
    expect(verdict).toBe('GROUNDED')
  })

  it('raises the message the server put in its error event', async () => {
    fetch.mockResolvedValue(
      streamResponse(200, 'event:error\ndata:{"message":"La conversation a échoué."}\n\n'),
    )

    await expect(
      askAgent('jeton-abc', 'Quel est le délai ?', { onToken: () => {}, onSources: () => {} }),
    ).rejects.toThrow('La conversation a échoué.')
  })

  it('raises when the stream stops before the end of the conversation', async () => {
    // A proxy that cuts, a server that dies: the events simply stop. Resolving here would
    // leave a half-written answer looking finished.
    fetch.mockResolvedValue(streamResponse(200, 'event:token\ndata:Quatorze\n\n'))

    await expect(
      askAgent('jeton-abc', 'Quel est le délai ?', { onToken: () => {}, onSources: () => {} }),
    ).rejects.toThrow("La conversation s'est interrompue avant la fin de la réponse.")
  })

  it('translates a 401 into an expired session', async () => {
    fetch.mockResolvedValue({ ok: false, status: 401, json: () => Promise.resolve(null) })

    await expect(
      askAgent('jeton-abc', 'Quel est le délai ?', { onToken: () => {}, onSources: () => {} }),
    ).rejects.toThrow(UnauthorizedError)
  })

  it('translates a 422 into a refusal on the question field', async () => {
    fetch.mockResolvedValue(
      jsonResponse(422, { errors: { question: 'La question ne peut pas être vide.' } }),
    )

    try {
      await askAgent('jeton-abc', '   ', { onToken: () => {}, onSources: () => {} })
      expect.unreachable('the refusal should have been raised')
    } catch (error) {
      expect(error).toBeInstanceOf(ValidationError)
      expect(error.errors).toEqual({ question: 'La question ne peut pas être vide.' })
    }
  })

  it('does not replace the failure with a syntax error when the body is not JSON', async () => {
    fetch.mockResolvedValue({
      ok: false,
      status: 502,
      json: () => Promise.reject(new SyntaxError('Unexpected token <')),
    })

    await expect(
      askAgent('jeton-abc', 'Quel est le délai ?', { onToken: () => {}, onSources: () => {} }),
    ).rejects.toThrow("La conversation n'a pas pu démarrer.")
  })
})

describe('Google Drive', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('starting an authorization', () => {
    it('posts on the authorizations route and returns the consent URL', async () => {
      fetch.mockResolvedValue(
        jsonResponse(201, { authorizationUrl: 'https://accounts.google.com/o/oauth2/v2/auth?x=1' }),
      )

      const url = await startDriveAuthorization('jeton-abc')

      const [route, options] = fetch.mock.calls[0]
      expect(route).toBe('/api/drive/authorizations')
      expect(options.method).toBe('POST')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(url).toBe('https://accounts.google.com/o/oauth2/v2/auth?x=1')
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(startDriveAuthorization('jeton-perime')).rejects.toThrow(UnauthorizedError)
    })

    it('does not replace the failure with a syntax error when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(startDriveAuthorization('jeton-abc')).rejects.toThrow(
        "La connexion à Google Drive n'a pas pu démarrer.",
      )
    })
  })

  describe('reading the connection', () => {
    it('reads the connection with the bearer token', async () => {
      const connection = {
        googleEmail: 'alice@example.com',
        status: 'ACTIVE',
        connectedAt: '2026-09-08T10:00:00Z',
      }
      fetch.mockResolvedValue(jsonResponse(200, connection))

      const result = await fetchDriveConnection('jeton-abc')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/drive/connection')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(result).toEqual(connection)
    })

    // No Drive connected is the state of every new account: raising here would show a
    // failure message on the first opening of the screen.
    it('reads a 404 as no Drive connected, not as a failure', async () => {
      fetch.mockResolvedValue(
        jsonResponse(404, { message: "Aucun compte Google Drive n'est connecté." }),
      )

      await expect(fetchDriveConnection('jeton-abc')).resolves.toBeNull()
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(fetchDriveConnection('jeton-perime')).rejects.toThrow(UnauthorizedError)
    })

    it('translates any other failure into a global message', async () => {
      fetch.mockResolvedValue(jsonResponse(500, null))

      await expect(fetchDriveConnection('jeton-abc')).rejects.toThrow(
        "La connexion Google Drive n'a pas pu être lue.",
      )
    })
  })

  describe('disconnecting the Drive', () => {
    it('sends a DELETE on the connection, with the bearer token', async () => {
      fetch.mockResolvedValue(jsonResponse(204, null))

      await disconnectDrive('jeton-abc')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/drive/connection')
      expect(options.method).toBe('DELETE')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(disconnectDrive('jeton-perime')).rejects.toThrow(UnauthorizedError)
    })

    it("displays the server's message as is when no connection is there any more", async () => {
      fetch.mockResolvedValue(
        jsonResponse(404, { message: "Aucun compte Google Drive n'est connecté." }),
      )

      await expect(disconnectDrive('jeton-abc')).rejects.toThrow(
        "Aucun compte Google Drive n'est connecté.",
      )
    })

    it('does not replace the failure with a syntax error when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(disconnectDrive('jeton-abc')).rejects.toThrow(
        "Le compte Google Drive n'a pas pu être déconnecté.",
      )
    })
  })

  describe('browsing the folders', () => {
    it('reads the root when no parent is given', async () => {
      const folders = [{ id: 'folder-1', name: 'Factures' }]
      fetch.mockResolvedValue(jsonResponse(200, folders))

      const result = await browseDriveFolders('jeton-abc')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/drive/folders')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(result).toEqual(folders)
    })

    it('carries the parent as a query parameter, encoded', async () => {
      fetch.mockResolvedValue(jsonResponse(200, []))

      await browseDriveFolders('jeton-abc', 'a b/c')

      expect(fetch.mock.calls[0][0]).toBe('/api/drive/folders?parent=a%20b%2Fc')
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(browseDriveFolders('jeton-perime')).rejects.toThrow(UnauthorizedError)
    })

    it("displays the server's message as is when no Drive is connected", async () => {
      fetch.mockResolvedValue(
        jsonResponse(409, {
          message: 'Connectez un compte Google Drive pour parcourir vos dossiers.',
        }),
      )

      await expect(browseDriveFolders('jeton-abc')).rejects.toThrow(
        'Connectez un compte Google Drive pour parcourir vos dossiers.',
      )
    })

    it("displays the server's message as is when Google is unreachable", async () => {
      fetch.mockResolvedValue(
        jsonResponse(503, { message: 'Google Drive est momentanément injoignable.' }),
      )

      await expect(browseDriveFolders('jeton-abc')).rejects.toThrow(
        'Google Drive est momentanément injoignable.',
      )
    })

    it('does not replace the failure with a syntax error when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(browseDriveFolders('jeton-abc')).rejects.toThrow(
        "Vos dossiers Google Drive n'ont pas pu être parcourus.",
      )
    })
  })

  describe('listing the watched folders', () => {
    it('reads the list with the bearer token', async () => {
      const folders = [{ id: 'watched-1', name: 'Factures', documentCount: 3, rejections: [] }]
      fetch.mockResolvedValue(jsonResponse(200, folders))

      const result = await listWatchedFolders('jeton-abc')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/drive/watched-folders')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(result).toEqual(folders)
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(listWatchedFolders('jeton-perime')).rejects.toThrow(UnauthorizedError)
    })

    it('translates any other failure into a global message', async () => {
      fetch.mockResolvedValue(jsonResponse(500, null))

      await expect(listWatchedFolders('jeton-abc')).rejects.toThrow(
        "La liste des dossiers surveillés n'a pas pu être chargée.",
      )
    })
  })

  describe('watching a folder', () => {
    it('posts the folder identifier as JSON, with the bearer token', async () => {
      fetch.mockResolvedValue(jsonResponse(201, null))

      await watchDriveFolder('jeton-abc', 'folder-1')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/drive/watched-folders')
      expect(options.method).toBe('POST')
      expect(options.headers['Content-Type']).toBe('application/json')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(JSON.parse(options.body)).toEqual({ folderId: 'folder-1' })
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(watchDriveFolder('jeton-perime', 'folder-1')).rejects.toThrow(UnauthorizedError)
    })

    it('translates a 422 into per-field errors', async () => {
      fetch.mockResolvedValue(
        jsonResponse(422, { errors: { folderId: 'Le dossier à surveiller est obligatoire.' } }),
      )

      try {
        await watchDriveFolder('jeton-abc', '')
        expect.unreachable('the refusal should have been raised')
      } catch (error) {
        expect(error).toBeInstanceOf(ValidationError)
        expect(error.errors).toEqual({ folderId: 'Le dossier à surveiller est obligatoire.' })
      }
    })

    it("displays the server's message as is for a folder already covered", async () => {
      fetch.mockResolvedValue(
        jsonResponse(409, { message: 'Ce dossier est déjà couvert par un dossier surveillé.' }),
      )

      await expect(watchDriveFolder('jeton-abc', 'folder-1')).rejects.toThrow(
        'Ce dossier est déjà couvert par un dossier surveillé.',
      )
    })

    it("displays the server's message as is for an unknown folder", async () => {
      fetch.mockResolvedValue(jsonResponse(404, { message: 'Ce dossier est introuvable.' }))

      await expect(watchDriveFolder('jeton-abc', 'folder-1')).rejects.toThrow(
        'Ce dossier est introuvable.',
      )
    })

    it("displays the server's message as is when Google is unreachable", async () => {
      fetch.mockResolvedValue(
        jsonResponse(503, { message: 'Google Drive est momentanément injoignable.' }),
      )

      await expect(watchDriveFolder('jeton-abc', 'folder-1')).rejects.toThrow(
        'Google Drive est momentanément injoignable.',
      )
    })

    it('does not replace the failure with a syntax error when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(watchDriveFolder('jeton-abc', 'folder-1')).rejects.toThrow(
        "Ce dossier n'a pas pu être surveillé.",
      )
    })
  })

  describe('unwatching a folder', () => {
    it('sends a DELETE on the watched folder, with the bearer token', async () => {
      fetch.mockResolvedValue(jsonResponse(204, null))

      await unwatchDriveFolder('jeton-abc', 'watched-1')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/drive/watched-folders/watched-1')
      expect(options.method).toBe('DELETE')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(unwatchDriveFolder('jeton-perime', 'watched-1')).rejects.toThrow(
        UnauthorizedError,
      )
    })

    it("displays the server's message as is for a folder that cannot be found", async () => {
      fetch.mockResolvedValue(
        jsonResponse(404, { message: 'Ce dossier surveillé est introuvable.' }),
      )

      await expect(unwatchDriveFolder('jeton-abc', 'watched-1')).rejects.toThrow(
        'Ce dossier surveillé est introuvable.',
      )
    })

    it('does not replace the failure with a syntax error when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(unwatchDriveFolder('jeton-abc', 'watched-1')).rejects.toThrow(
        "Ce dossier n'a pas pu être retiré de la surveillance.",
      )
    })
  })

  describe('importing a watched folder', () => {
    it('posts on the import route, with the bearer token', async () => {
      fetch.mockResolvedValue(jsonResponse(202, null))

      await importWatchedFolder('jeton-abc', 'watched-1')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/drive/watched-folders/watched-1/import')
      expect(options.method).toBe('POST')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(jsonResponse(401, null))

      await expect(importWatchedFolder('jeton-perime', 'watched-1')).rejects.toThrow(
        UnauthorizedError,
      )
    })

    it("displays the server's message as is for a folder that cannot be found", async () => {
      fetch.mockResolvedValue(
        jsonResponse(404, { message: 'Ce dossier surveillé est introuvable.' }),
      )

      await expect(importWatchedFolder('jeton-abc', 'watched-1')).rejects.toThrow(
        'Ce dossier surveillé est introuvable.',
      )
    })

    it('does not replace the failure with a syntax error when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(importWatchedFolder('jeton-abc', 'watched-1')).rejects.toThrow(
        "L'import de ce dossier n'a pas pu être demandé.",
      )
    })
  })
})
