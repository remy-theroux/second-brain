// Server-Sent Events read from a `fetch` response body. `EventSource` is not an option: the
// chat route is a POST, and EventSource only ever does GET.

/**
 * Yields one `{ event, data }` per frame, in order. `data` is the payload verbatim: Spring's
 * `SseEmitter.SseEventBuilderImpl` splits a multi-line fragment across several `data:` lines,
 * which are rejoined here with a newline.
 */
export async function* readServerSentEvents(body) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        // Whatever is left is a frame the server never finished: handing over half an
        // event would be worse than dropping it.
        return
      }
      // `stream: true` holds back a multi-byte character split across two chunks.
      buffer += decoder.decode(value, { stream: true })

      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1) {
        const frame = parse(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        if (frame) {
          yield frame
        }
        boundary = buffer.indexOf('\n\n')
      }
    }
  } finally {
    reader.releaseLock()
  }
}

function parse(frame) {
  let event = 'message'
  const data = []

  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length)
    } else if (line.startsWith('data:')) {
      // `SseEmitter.SseEventBuilderImpl` writes `data:` without a trailing space, so the one
      // the specification says to strip belongs to the fragment: removing it would weld the
      // last word of a fragment onto the first of the next.
      data.push(line.slice('data:'.length))
    }
  }

  return data.length === 0 ? null : { event, data: data.join('\n') }
}
