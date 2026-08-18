import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { register, ValidationError } from '@/api/client'

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
