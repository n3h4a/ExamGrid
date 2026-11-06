import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import type { RunEvent } from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { streamEvents } from '../../lib/sse';

/**
 * Live progress of a run over SSE. Reconnects with backoff after network drops (the server
 * replays the latest event on connect), stops after the terminal "status" event and then
 * refreshes the run's queries so the page shows the stored result.
 */
export function useRunEvents(runId: number, enabled: boolean): RunEvent | null {
  const { token } = useAuth();
  const queryClient = useQueryClient();
  const [event, setEvent] = useState<RunEvent | null>(null);

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    let finished = false;
    let attempt = 0;

    async function connect() {
      while (!finished && !controller.signal.aborted) {
        try {
          await streamEvents(`/api/schedule-runs/${runId}/events`, token, (m) => {
            const next = JSON.parse(m.data) as RunEvent;
            attempt = 0;
            setEvent(next);
            if (m.event === 'status') {
              finished = true;
              void queryClient.invalidateQueries({ queryKey: ['run', runId] });
              void queryClient.invalidateQueries({ queryKey: ['runs'] });
            }
          }, controller.signal);
        } catch {
          if (controller.signal.aborted) return;
        }
        if (!finished && !controller.signal.aborted) {
          attempt += 1;
          await new Promise((resolve) => setTimeout(resolve, Math.min(10_000, 500 * 2 ** attempt)));
        }
      }
    }
    void connect();
    return () => controller.abort();
  }, [runId, enabled, token, queryClient]);

  return event;
}
