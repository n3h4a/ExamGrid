import { SseParser, type SseMessage } from './sse';

describe('SseParser', () => {
  it('parses named events across arbitrary chunk boundaries and skips comments', () => {
    const messages: SseMessage[] = [];
    const parser = new SseParser((m) => messages.push(m));
    parser.push('event:progress\nid:7-10\ndata:{"pct":');
    parser.push('10}\n\n:keep-alive\n\n');
    parser.push('event: status\r\ndata: {"pct":100}\r\n\r\n');
    expect(messages).toEqual([
      { event: 'progress', id: '7-10', data: '{"pct":10}' },
      { event: 'status', id: undefined, data: '{"pct":100}' },
    ]);
  });

  it('joins multi-line data and defaults the event name', () => {
    const messages: SseMessage[] = [];
    new SseParser((m) => messages.push(m)).push('data: line 1\ndata: line 2\n\n');
    expect(messages).toEqual([{ event: 'message', id: undefined, data: 'line 1\nline 2' }]);
  });
});
