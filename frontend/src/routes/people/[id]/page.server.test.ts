import { describe, expect, it, vi } from 'vitest';

vi.mock('$lib/server/operations', () => ({
  getPerson: vi.fn()
}));

vi.mock('$lib/server/media', () => ({
  deletePersonPhoto: vi.fn()
}));

import { load } from './+page.server';
import { getPerson } from '$lib/server/operations';

describe('/people/[id] load', () => {
  it('versions the photo URL with the record version so a re-upload busts the 30-day browser cache (PersonPhotoController)', async () => {
    vi.mocked(getPerson).mockResolvedValueOnce({ id: 'p1', version: 3 } as any);
    const data = (await load({
      params: { id: 'p1' },
      request: { headers: new Headers() }
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)) as any;
    expect(data.photoUrl).toBe('/api/people/p1/photo?v=3');

    vi.mocked(getPerson).mockResolvedValueOnce({ id: 'p1', version: 4 } as any);
    const reloaded = (await load({
      params: { id: 'p1' },
      request: { headers: new Headers() }
    } as any)) as any;
    expect(reloaded.photoUrl).toBe('/api/people/p1/photo?v=4');
  });
});
