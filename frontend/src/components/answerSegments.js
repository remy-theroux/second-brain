// A citation is written `[n]` and nothing else. The server's CitationPolicy owns that syntax
// on its side; this is its only counterpart on the screen.
const CITATION = /\[(\d+)\]/g

/**
 * Cuts an answer into what a screen has to render: `{ text }` for prose, `{ number, text }`
 * for a citation whose source is known. A marker with no matching source stays prose — the
 * sources arrive after the text, and a link to nothing is worse than a raw marker.
 */
export function answerSegments(text, sources) {
  const known = new Set(sources.map((source) => source.number))
  const segments = []
  let cursor = 0

  for (const match of text.matchAll(CITATION)) {
    const number = Number(match[1])
    if (!known.has(number)) {
      continue
    }
    if (match.index > cursor) {
      segments.push({ text: text.slice(cursor, match.index) })
    }
    segments.push({ number, text: match[0] })
    cursor = match.index + match[0].length
  }

  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor) })
  }
  return segments
}
