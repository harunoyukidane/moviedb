import { describe, expect, it, vi, beforeEach } from 'vitest';

const listPeopleMock = vi.fn();

vi.mock('$lib/server/operations', () => ({
  listPeople: (...args: unknown[]) => listPeopleMock(...args)
}));

import { load } from './+page.server';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function makeEvent(search: string): any {
  return {
    url: new URL(`http://localhost/people${search}`),
    request: new Request(`http://localhost/people${search}`)
  };
}

describe('/people load', () => {
  beforeEach(() => listPeopleMock.mockReset());

  it('passes through photoUrl on each item unchanged', async () => {
    listPeopleMock.mockResolvedValue({
      items: [
        { id: 'p1', name: 'Has Photo', birthDate: null, deathDate: null, version: 0, photoUrl: '/api/people/p1/photo' },
        { id: 'p2', name: 'No Photo', birthDate: null, deathDate: null, version: 0, photoUrl: null }
      ],
      total: 2,
      limit: 24,
      offset: 0
    });

    const result = (await load(makeEvent(''))) as any;
    expect(listPeopleMock).toHaveBeenCalledWith(null, 24, 0, expect.anything());
    expect(result.page.items[0].photoUrl).toEqual('/api/people/p1/photo');
    expect(result.page.items[1].photoUrl).toBeNull();
  });
});
