/**
 * Minimal Server-Sent Events parser over a fetch stream. EventSource cannot send an
 * Authorization header, so ExamGrid reads the stream itself: the bearer token stays out of
 * URLs and logs.
 */
export interface SseMessage {
  event: string;
  data: string;
  id?: string;
}

/** Feeds text chunks and emits complete messages (blank-line terminated). */
export class SseParser {
  private buffer = '';

  constructor(private readonly onMessage: (message: SseMessage) => void) {}

  push(chunk: string): void {
    this.buffer += chunk.replace(/\r\n?/g, '\n');
    let boundary = this.buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const block = this.buffer.slice(0, boundary);
      this.buffer = this.buffer.slice(boundary + 2);
      this.dispatch(block);
      boundary = this.buffer.indexOf('\n\n');
    }
  }

  private dispatch(block: string) {
    let event = 'message';
    let id: string | undefined;
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (!line || line.startsWith(':')) continue; // comments are keep-alives
      const colon = line.indexOf(':');
      const field = colon < 0 ? line : line.slice(0, colon);
      const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '');
      if (field === 'event') event = value;
      else if (field === 'data') data.push(value);
      else if (field === 'id') id = value;
    }
    if (data.length > 0) this.onMessage({ event, data: data.join('\n'), id });
  }
}

/**
 * Opens an authenticated SSE stream and calls {@code onMessage} until the server closes it or
 * {@code signal} aborts. Resolves when the stream ends; rejects on HTTP or network errors.
 */
export async function streamEvents(url: string, token: string | null, onMessage: (m: SseMessage) => void, signal: AbortSignal): Promise<void> {
  const response = await fetch(url, {
    headers: { Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    signal,
  });
  if (!response.ok || !response.body) throw new Error(`Event stream failed (${response.status})`);
  const parser = new SseParser(onMessage);
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  for (;;) {
    const { value, done } = await reader.read();
    if (done) return;
    parser.push(value);
  }
}
