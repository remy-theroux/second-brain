import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  askAgent,
  deleteDocument,
  DuplicateDocumentError,
  fetchDocument,
  listDocuments,
  register,
  UnauthorizedError,
  uploadDocument,
  ValidationError,
} from '@/api/client'

// Minimal response: only the status and the JSON body matter for this module.
function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
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
