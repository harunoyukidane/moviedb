import { afterEach, describe, expect, it, vi } from 'vitest';
import { proxyMediaGet } from './media-proxy';

describe('proxyMediaGet', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('streams successful responses and preserves cache headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('image', {
        status: 200,
        headers: { 'content-type': 'image/png', etag: '"hash"', 'cache-control': 'public' }
      })
    );
    vi.stubGlobal('fetch', fetchMock);

    const response = await proxyMediaGet(
      'http://media/object',
      new Request('http://frontend/image', { headers: { 'if-none-match': '"old"' } })
    );

    expect(fetchMock).toHaveBeenCalledWith('http://media/object', {
      headers: { 'If-None-Match': '"old"' }
    });
    expect(response.status).toBe(200);
    expect(response.headers.get('etag')).toBe('"hash"');
    expect(await response.text()).toBe('image');
  });

  it('forwards the Accept header so upstream can content-negotiate a WebP variant', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('image', {
        status: 200,
        headers: { 'content-type': 'image/webp', vary: 'Accept' }
      })
    );
    vi.stubGlobal('fetch', fetchMock);

    const response = await proxyMediaGet(
      'http://media/object',
      new Request('http://frontend/image', { headers: { accept: 'image/webp,image/*' } })
    );

    expect(fetchMock).toHaveBeenCalledWith('http://media/object', {
      headers: { Accept: 'image/webp,image/*' }
    });
    expect(response.headers.get('content-type')).toBe('image/webp');
    expect(response.headers.get('vary')).toBe('Accept');
  });

  it('returns a gateway error when the media service is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));

    const response = await proxyMediaGet('http://media/object', new Request('http://frontend/image'));

    expect(response.status).toBe(502);
  });
});
