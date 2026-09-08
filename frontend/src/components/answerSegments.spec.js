import { describe, expect, it } from 'vitest'
import { answerSegments } from '@/components/answerSegments'

const SOURCES = [
  { number: 1, documentId: 'doc-1', filename: 'rapport.pdf', position: 3, heading: 'A', text: 'x' },
  { number: 2, documentId: 'doc-2', filename: 'notes.md', position: 0, heading: 'B', text: 'y' },
]

describe('answer segments', () => {
  it('turns a known citation into a segment of its own', () => {
    expect(answerSegments('Quatorze jours [1].', SOURCES)).toEqual([
      { text: 'Quatorze jours ' },
      { number: 1, text: '[1]' },
      { text: '.' },
    ])
  })

  it('keeps the whole text when no citation is present', () => {
    expect(answerSegments('Bonjour.', SOURCES)).toEqual([{ text: 'Bonjour.' }])
  })

  it('leaves a marker whose source is unknown as prose', () => {
    // Two cases meet here: the sources arrive after the text, so everything is unknown while
    // it streams; and a real document often carries its own footnote calls, which the model
    // copies. Neither must produce a link to nothing.
    expect(answerSegments('Voir [7] et [1].', SOURCES)).toEqual([
      { text: 'Voir [7] et ' },
      { number: 1, text: '[1]' },
      { text: '.' },
    ])
  })

  it('handles several citations in a row', () => {
    expect(answerSegments('[1][2]', SOURCES)).toEqual([
      { number: 1, text: '[1]' },
      { number: 2, text: '[2]' },
    ])
  })

  it('returns nothing for an empty text', () => {
    expect(answerSegments('', SOURCES)).toEqual([])
  })

  it('preserves the line breaks of the text it cuts', () => {
    expect(answerSegments('Premier [1].\n\nSecond.', SOURCES)).toEqual([
      { text: 'Premier ' },
      { number: 1, text: '[1]' },
      { text: '.\n\nSecond.' },
    ])
  })
})
