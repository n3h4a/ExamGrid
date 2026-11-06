import { ApiError, api, buildUrl, configureClient } from './client';
import { stubFetch } from '../test/render';

describe('api client', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('builds query strings and skips empty values', () => {
    expect(buildUrl('/api/courses', { q: 'cs', page: 0, size: undefined, department: '' })).toBe('/api/courses?q=cs&page=0');
    expect(buildUrl('/api/x')).toBe('/api/x');
  });

  it('sends the bearer token and parses JSON', async () => {
    configureClient(() => 'abc', () => {});
    const fetchMock = stubFetch({ '/api/auth/me': { body: { username: 'admin' } } });
    await expect(api.get('/api/auth/me')).resolves.toEqual({ username: 'admin' });
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer abc');
  });

  it('turns problem details into ApiError with code, field errors and request id', async () => {
    configureClient(() => null, () => {});
    stubFetch({
      '/api/users': {
        status: 400,
        body: { status: 400, code: 'validation_failed', detail: 'The request has invalid fields.', requestId: 'r-1', errors: [{ field: 'password', message: 'too short' }] },
      },
    });
    const error = await api.post('/api/users', {}).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.code).toBe('validation_failed');
    expect(apiError.message).toBe('The request has invalid fields.');
    expect(apiError.fieldErrors).toEqual([{ field: 'password', message: 'too short' }]);
    expect(apiError.requestId).toBe('r-1');
  });

  it('calls the unauthorized handler on 401', async () => {
    const onUnauthorized = vi.fn();
    configureClient(() => 'expired', onUnauthorized);
    stubFetch({ '/api/auth/me': { status: 401, body: { status: 401, code: 'unauthenticated' } } });
    await expect(api.get('/api/auth/me')).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('returns undefined for 204 and does not send JSON headers for uploads', async () => {
    configureClient(() => null, () => {});
    const fetchMock = stubFetch({ '/api/rooms/1': { status: 204 }, '/api/imports': { body: { ok: true } } });
    await expect(api.delete('/api/rooms/1')).resolves.toBeUndefined();
    await api.upload('/api/imports/courses', new File(['a,b'], 'c.csv'), { dryRun: true });
    const init = fetchMock.mock.calls[1][1] as RequestInit;
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined();
    expect(String(fetchMock.mock.calls[1][0])).toBe('/api/imports/courses?dryRun=true');
  });
});
