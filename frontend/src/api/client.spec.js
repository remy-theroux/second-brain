import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  deleteDocument,
  DuplicateDocumentError,
  fetchDocument,
  listDocuments,
  register,
  UnauthorizedError,
  uploadDocument,
  ValidationError,
} from '@/api/client'

// Réponse minimale : seuls le statut et le corps JSON comptent pour ce module.
function reponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }
}

describe('création de compte', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('poste la saisie en JSON sur la route des inscriptions', async () => {
    fetch.mockResolvedValue(reponse(201, null))

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

  it('traduit un 422 en erreurs par champ', async () => {
    fetch.mockResolvedValue(reponse(422, { errors: { email: "L'email n'est pas valide." } }))

    await expect(register('pas-un-email', 'chevalpile42')).rejects.toThrow(ValidationError)

    try {
      await register('pas-un-email', 'chevalpile42')
    } catch (error) {
      expect(error.errors).toEqual({ email: "L'email n'est pas valide." })
    }
  })

  it('traduit un 503 en message global', async () => {
    fetch.mockResolvedValue(reponse(503, { message: "L'email n'a pas pu être envoyé." }))

    await expect(register('alice@example.com', 'chevalpile42')).rejects.toThrow(
      "L'email n'a pas pu être envoyé.",
    )
  })

  it("ne remplace pas l'échec par une erreur de syntaxe quand le corps n'est pas du JSON", async () => {
    // Un proxy en panne rend du HTML : le parsing échoue, mais l'utilisateur doit lire
    // un message utile, pas « Unexpected token < ».
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

describe('base de connaissance', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('liste des documents', () => {
    it('lit la liste avec le jeton du porteur', async () => {
      const documents = [{ id: 'doc-1', filename: 'notes.md', status: 'PENDING' }]
      fetch.mockResolvedValue(reponse(200, documents))

      const result = await listDocuments('jeton-abc')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(result).toEqual(documents)
    })

    it('traduit un 401 en session expirée', async () => {
      fetch.mockResolvedValue(reponse(401, null))

      await expect(listDocuments('jeton-perime')).rejects.toThrow(UnauthorizedError)
    })

    it('traduit toute autre panne en message global', async () => {
      fetch.mockResolvedValue(reponse(500, null))

      await expect(listDocuments('jeton-abc')).rejects.toThrow(
        "La liste des documents n'a pas pu être chargée.",
      )
    })
  })

  describe("lecture d'un document", () => {
    it('lit le document et son extraction avec le jeton du porteur', async () => {
      const attendu = {
        id: 'doc-1',
        filename: 'notes.md',
        type: 'TEXTUAL',
        status: 'EXTRACTED',
        extraction: { extractedAt: '2026-08-26T10:00:00Z', characterCount: 120, blocks: [] },
      }
      fetch.mockResolvedValue(reponse(200, attendu))

      const document = await fetchDocument('jeton-abc', 'doc-1')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents/doc-1')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(document).toEqual(attendu)
    })

    it('traduit un 401 en session expirée', async () => {
      fetch.mockResolvedValue(reponse(401, null))

      await expect(fetchDocument('jeton-perime', 'doc-1')).rejects.toThrow(UnauthorizedError)
    })

    it('rend le message du serveur sur un document introuvable', async () => {
      fetch.mockResolvedValue(reponse(404, { message: 'Ce document est introuvable.' }))

      await expect(fetchDocument('jeton-abc', 'doc-1')).rejects.toThrow(
        'Ce document est introuvable.',
      )
    })

    it("rend un message par défaut quand le corps n'est pas du JSON", async () => {
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

  describe("dépôt d'un document", () => {
    const fichier = new File(['# Notes'], 'notes.md', { type: 'text/markdown' })

    it('poste le fichier en multipart sous le nom « file », avec le jeton du porteur', async () => {
      fetch.mockResolvedValue(reponse(201, null))

      await uploadDocument('jeton-abc', fichier)

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents')
      expect(options.method).toBe('POST')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(options.body).toBeInstanceOf(FormData)
      expect(options.body.get('file')).toBe(fichier)
      // Le navigateur pose lui-même le Content-Type multipart avec son boundary : le
      // fixer à la main le priverait du boundary, et le serveur ne saurait plus découper.
      expect(options.headers['Content-Type']).toBeUndefined()
    })

    it('traduit un 401 en session expirée', async () => {
      fetch.mockResolvedValue(reponse(401, null))

      await expect(uploadDocument('jeton-perime', fichier)).rejects.toThrow(UnauthorizedError)
    })

    it('traduit un 409 en doublon désignant le document existant', async () => {
      fetch.mockResolvedValue(
        reponse(409, { message: 'Ce document est déjà présent.', existingDocumentId: 'doc-1' }),
      )

      await expect(uploadDocument('jeton-abc', fichier)).rejects.toThrow(DuplicateDocumentError)

      try {
        await uploadDocument('jeton-abc', fichier)
      } catch (error) {
        expect(error.message).toBe('Ce document est déjà présent.')
        expect(error.existingDocumentId).toBe('doc-1')
      }
    })

    it('traduit un 422 en erreurs par champ', async () => {
      fetch.mockResolvedValue(reponse(422, { errors: { file: 'Le fichier est obligatoire.' } }))

      await expect(uploadDocument('jeton-abc', fichier)).rejects.toThrow(ValidationError)

      try {
        await uploadDocument('jeton-abc', fichier)
      } catch (error) {
        expect(error.errors).toEqual({ file: 'Le fichier est obligatoire.' })
      }
    })

    it('affiche tel quel le message du serveur pour un format refusé', async () => {
      fetch.mockResolvedValue(
        reponse(415, { message: 'Formats acceptés : .pdf, .md, .txt, .docx.' }),
      )

      await expect(uploadDocument('jeton-abc', fichier)).rejects.toThrow(
        'Formats acceptés : .pdf, .md, .txt, .docx.',
      )
    })

    it('affiche tel quel le message du serveur pour un fichier trop volumineux', async () => {
      fetch.mockResolvedValue(
        reponse(413, { message: 'Ce fichier dépasse la taille maximale acceptée.' }),
      )

      await expect(uploadDocument('jeton-abc', fichier)).rejects.toThrow(
        'Ce fichier dépasse la taille maximale acceptée.',
      )
    })

    it("ne remplace pas l'échec par une erreur de syntaxe quand le corps n'est pas du JSON", async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(uploadDocument('jeton-abc', fichier)).rejects.toThrow(
        "Le document n'a pas pu être déposé.",
      )
    })
  })

  describe("suppression d'un document", () => {
    it('envoie un DELETE sur le document, avec le jeton du porteur', async () => {
      fetch.mockResolvedValue(reponse(204, null))

      await deleteDocument('jeton-abc', 'doc-1')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents/doc-1')
      expect(options.method).toBe('DELETE')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
    })

    it('traduit un 401 en session expirée', async () => {
      fetch.mockResolvedValue(reponse(401, null))

      await expect(deleteDocument('jeton-perime', 'doc-1')).rejects.toThrow(UnauthorizedError)
    })

    it('affiche tel quel le message du serveur pour un document introuvable', async () => {
      fetch.mockResolvedValue(reponse(404, { message: "Ce document n'existe pas." }))

      await expect(deleteDocument('jeton-abc', 'doc-1')).rejects.toThrow(
        "Ce document n'existe pas.",
      )
    })

    it("ne remplace pas l'échec par une erreur de syntaxe quand le corps n'est pas du JSON", async () => {
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
