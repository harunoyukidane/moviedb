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

  it('passes a trimmed query string through to listPeople', async () => {
    listPeopleMock.mockResolvedValue({ items: [], total: 0, limit: 24, offset: 0 });
    const result = (await load(makeEvent('?q=%20Keanu%20'))) as any;
    expect(listPeopleMock).toHaveBeenCalledWith('Keanu', 24, 0, expect.anything());
    expect(result.query).toEqual('Keanu');
  });

  it('collapses a blank query to null', async () => {
    listPeopleMock.mockResolvedValue({ items: [], total: 0, limit: 24, offset: 0 });
    const result = (await load(makeEvent('?q=%20%20'))) as any;
    expect(listPeopleMock).toHaveBeenCalledWith(null, 24, 0, expect.anything());
    expect(result.query).toBeNull();
  });

  it('clamps an overlong query to 100 characters', async () => {
    listPeopleMock.mockResolvedValue({ items: [], total: 0, limit: 24, offset: 0 });
    const long = 'a'.repeat(150);
    const result = (await load(makeEvent(`?q=${long}`))) as any;
    expect(result.query).toHaveLength(100);
    expect(listPeopleMock).toHaveBeenCalledWith('a'.repeat(100), 24, 0, expect.anything());
  });

  it('retains the query across pagination via the offset param', async () => {
    listPeopleMock.mockResolvedValue({ items: [], total: 50, limit: 24, offset: 24 });
    const result = (await load(makeEvent('?q=Keanu&offset=24'))) as any;
    expect(listPeopleMock).toHaveBeenCalledWith('Keanu', 24, 24, expect.anything());
    expect(result.query).toEqual('Keanu');
  });
});
