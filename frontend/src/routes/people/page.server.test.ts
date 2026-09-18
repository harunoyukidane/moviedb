import { describe, expect, it, vi, beforeEach } from 'vitest';

const listPeopleMock = vi.fn();
const personNameOffsetMock = vi.fn();

vi.mock('$lib/server/operations', () => ({
  listPeople: (...args: unknown[]) => listPeopleMock(...args),
  personNameOffset: (...args: unknown[]) => personNameOffsetMock(...args)
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
  beforeEach(() => {
    listPeopleMock.mockReset();
    personNameOffsetMock.mockReset();
  });

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

  it('resolves the offset from personNameOffset when a letter param is present', async () => {
    personNameOffsetMock.mockResolvedValue(137);
    listPeopleMock.mockResolvedValue({ items: [], total: 500, limit: 24, offset: 137 });
    const result = (await load(makeEvent('?letter=C'))) as any;
    expect(personNameOffsetMock).toHaveBeenCalledWith('C', null, expect.anything());
    expect(listPeopleMock).toHaveBeenCalledWith(null, 24, 137, expect.anything());
    expect(result.page.offset).toEqual(137);
  });

  it('uppercases the letter param and passes the active query through to personNameOffset', async () => {
    personNameOffsetMock.mockResolvedValue(10);
    listPeopleMock.mockResolvedValue({ items: [], total: 10, limit: 24, offset: 10 });
    await load(makeEvent('?letter=c&q=Keanu'));
    expect(personNameOffsetMock).toHaveBeenCalledWith('C', 'Keanu', expect.anything());
  });

  it('prefers letter over a simultaneous offset param', async () => {
    personNameOffsetMock.mockResolvedValue(50);
    listPeopleMock.mockResolvedValue({ items: [], total: 500, limit: 24, offset: 50 });
    await load(makeEvent('?letter=D&offset=200'));
    expect(listPeopleMock).toHaveBeenCalledWith(null, 24, 50, expect.anything());
  });

  it('ignores an invalid letter param and falls back to the offset param', async () => {
    listPeopleMock.mockResolvedValue({ items: [], total: 0, limit: 24, offset: 24 });
    await load(makeEvent('?letter=1&offset=24'));
    expect(personNameOffsetMock).not.toHaveBeenCalled();
    expect(listPeopleMock).toHaveBeenCalledWith(null, 24, 24, expect.anything());
  });

  it('falls back to the last page when a letter jump lands past the end of the list', async () => {
    personNameOffsetMock.mockResolvedValue(500);
    listPeopleMock
      .mockResolvedValueOnce({ items: [], total: 500, limit: 24, offset: 500 })
      .mockResolvedValueOnce({
        items: [{ id: 'p1', name: 'Zendaya', birthDate: null, deathDate: null, version: 0, photoUrl: null }],
        total: 500,
        limit: 24,
        offset: 480
      });
    const result = (await load(makeEvent('?letter=Z'))) as any;
    expect(listPeopleMock).toHaveBeenNthCalledWith(1, null, 24, 500, expect.anything());
    expect(listPeopleMock).toHaveBeenNthCalledWith(2, null, 24, 480, expect.anything());
    expect(result.page.items).toHaveLength(1);
    expect(result.page.offset).toEqual(480);
  });

  it('does not refetch when the catalogue is genuinely empty', async () => {
    personNameOffsetMock.mockResolvedValue(0);
    listPeopleMock.mockResolvedValue({ items: [], total: 0, limit: 24, offset: 0 });
    await load(makeEvent('?letter=Z'));
    expect(listPeopleMock).toHaveBeenCalledTimes(1);
  });
});
