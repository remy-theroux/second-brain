import { describe, expect, it } from 'vitest'
import { readServerSentEvents } from '@/api/sse'

// Turns text pieces into the byte stream a `fetch` response exposes. Each piece is one
// network chunk: that is how frame boundaries get to fall in awkward places.
function streamOf(...pieces) {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      pieces.forEach((piece) => controller.enqueue(encoder.encode(piece)))
      controller.close()
    },
  })
}

async function collect(stream) {
  const events = []
  for await (const event of readServerSentEvents(stream)) {
    events.push(event)
  }
  return events
}

describe('server-sent events reader', () => {
  it('reads the name and the payload of each event, in order', async () => {
    const events = await collect(
      streamOf('event:token\ndata:Quatorze\n\nevent:done\ndata:{"verdict":"GROUNDED"}\n\n'),
    )

    expect(events).toEqual([
      { event: 'token', data: 'Quatorze' },
      { event: 'done', data: '{"verdict":"GROUNDED"}' },
    ])
  })

  it('rejoins the several data lines of a multi-line payload', async () => {
    // The server splits a line break into a new `data:` line: rejoining without the
    // newline would run two paragraphs of the answer together.
    const events = await collect(streamOf('event:token\ndata:Premier\ndata:\ndata:Second\n\n'))

    expect(events).toEqual([{ event: 'token', data: 'Premier\n\nSecond' }])
  })

  it('keeps the space that opens a payload', async () => {
    // The server writes `data:` with no space of its own: the one seen here belongs to the
    // fragment. Stripping it would weld two words together.
    const events = await collect(streamOf('event:token\ndata: jours [1].\n\n'))

    expect(events).toEqual([{ event: 'token', data: ' jours [1].' }])
  })

  it('reassembles an event split across two network chunks', async () => {
    const events = await collect(streamOf('event:tok', 'en\ndata:Quat', 'orze\n\n'))

    expect(events).toEqual([{ event: 'token', data: 'Quatorze' }])
  })

  it('reassembles a multi-byte character split across two chunks', async () => {
    const encoder = new TextEncoder()
    const bytes = encoder.encode('event:token\ndata:été\n\n')
    const cut = bytes.indexOf(0xc3) + 1
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(bytes.slice(0, cut))
        controller.enqueue(bytes.slice(cut))
        controller.close()
      },
    })

    expect(await collect(stream)).toEqual([{ event: 'token', data: 'été' }])
  })

  it('ignores a frame that carries no payload', async () => {
    // A comment or a keep-alive: nothing to hand to the caller.
    const events = await collect(streamOf(':ping\n\nevent:token\ndata:Quatorze\n\n'))

    expect(events).toEqual([{ event: 'token', data: 'Quatorze' }])
  })

  it('drops a truncated trailing frame rather than handing over half an event', async () => {
    const events = await collect(streamOf('event:token\ndata:Quatorze\n\nevent:done\ndata:{'))

    expect(events).toEqual([{ event: 'token', data: 'Quatorze' }])
  })
})
