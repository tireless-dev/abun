import { describe, expect, it, vi } from 'vitest';
import { ApiError, createAbunApiClient } from '../client.ts';

describe('api client', () => {
  it('injects bearer token and retries idempotent reads', async () => {
    let calls = 0;
    const fetchImpl: typeof fetch = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
      calls += 1;
      if (calls === 1) {
        throw new Error('network');
      }
      expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer token-1');
      return new Response(JSON.stringify([]), { status: 200 });
    });

    const client = createAbunApiClient({ bearerToken: 'token-1', fetchImpl, maxReadRetries: 1, baseUrl: 'http://x' });
    const tasks = await client.listTasks();
    expect(tasks).toEqual([]);
    expect(calls).toBe(2);
  });

  it('maps api errors', async () => {
    const fetchImpl: typeof fetch = vi.fn(async () => new Response(JSON.stringify({ message: 'bad request' }), { status: 400 }));
    const client = createAbunApiClient({ fetchImpl, baseUrl: 'http://x' });
    await expect(client.listTasks()).rejects.toBeInstanceOf(ApiError);
  });
});
