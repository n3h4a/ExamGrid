import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { AuthProvider } from '../../auth/AuthProvider';
import { signIn } from '../../test/render';
import { useRunEvents } from './useRunEvents';

function Probe() {
  const event = useRunEvents(7, true);
  return <p>{event ? `${event.status} ${event.pct}% ${event.phase}` : 'waiting'}</p>;
}

function streamOf(chunks: string[]): Response {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      const encoder = new TextEncoder();
      chunks.forEach((c) => controller.enqueue(encoder.encode(c)));
      controller.close();
    },
  });
  return new Response(body, { headers: { 'Content-Type': 'text/event-stream' } });
}

describe('useRunEvents', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('streams progress with the bearer token and stops at the status event', async () => {
    signIn();
    const fetchMock = vi.fn(async () =>
      streamOf([
        'event:progress\ndata:{"runId":7,"status":"RUNNING","phase":"OPTIMISING","pct":42,"score":12,"message":"annealing"}\n\n',
        'event:status\ndata:{"runId":7,"status":"DONE","phase":"DONE","pct":100,"score":0,"message":"plan ready"}\n\n',
      ]),
    );
    vi.stubGlobal('fetch', fetchMock);
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    render(
      <QueryClientProvider client={client}>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </QueryClientProvider>,
    );
    expect(await screen.findByText('DONE 100% DONE')).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/schedule-runs/7/events');
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer test-token');
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['run', 7] });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
