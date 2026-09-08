import { describe, expect, it } from 'vitest'
import { hasUnsettled, isSettled, STATUS_LABELS, STATUS_SEVERITIES } from './documentStatus'

describe('isSettled', () => {
  it('holds READY and FAILED as settled', () => {
    expect(isSettled('READY')).toBe(true)
    expect(isSettled('FAILED')).toBe(true)
  })

  it('holds PENDING and EXTRACTED as still moving', () => {
    expect(isSettled('PENDING')).toBe(false)
    expect(isSettled('EXTRACTED')).toBe(false)
  })

  // A status the server adds without the front knowing must keep the clock running rather
  // than freeze a list that is still moving.
  it('holds an unknown status as still moving', () => {
    expect(isSettled('WHATEVER')).toBe(false)
  })
})

describe('hasUnsettled', () => {
  it('is false on an empty list', () => {
    expect(hasUnsettled([])).toBe(false)
  })

  it('is false when every document has settled', () => {
    expect(hasUnsettled([{ status: 'READY' }, { status: 'FAILED' }])).toBe(false)
  })

  it('is true as soon as one document is still moving', () => {
    expect(hasUnsettled([{ status: 'READY' }, { status: 'PENDING' }])).toBe(true)
  })
})

describe('the status tables', () => {
  it('label and colour every status', () => {
    for (const status of ['PENDING', 'EXTRACTED', 'READY', 'FAILED']) {
      expect(STATUS_LABELS[status]).toBeTruthy()
      expect(STATUS_SEVERITIES[status]).toBeTruthy()
    }
  })
})
