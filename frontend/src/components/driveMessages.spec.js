import { describe, expect, it } from 'vitest'
import { driveMessage } from './driveMessages'

describe('driveMessage', () => {
  it('has a label and a severity for every code the server can redirect with', () => {
    for (const code of ['ok', 'refus', 'lien-invalide', 'echec']) {
      expect(driveMessage(code).text).toBeTruthy()
      expect(driveMessage(code).severity).toBeTruthy()
    }
  })

  it('holds a successful authorization as a success, and the refusals as errors', () => {
    expect(driveMessage('ok').severity).toBe('success')
    expect(driveMessage('refus').severity).toBe('error')
    expect(driveMessage('lien-invalide').severity).toBe('error')
    expect(driveMessage('echec').severity).toBe('error')
  })

  it('says nothing when no code is present', () => {
    expect(driveMessage(undefined)).toBeNull()
    expect(driveMessage(null)).toBeNull()
    expect(driveMessage('')).toBeNull()
  })

  // Silence would let a failed authorization look like a successful one: a code the front
  // does not know must still say that something went wrong.
  it('falls back on a generic failure for an unknown code', () => {
    expect(driveMessage('quoi-que-ce-soit').text).toBeTruthy()
    expect(driveMessage('quoi-que-ce-soit').severity).toBe('error')
  })
})
