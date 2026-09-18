import { describe, expect, it, vi, beforeEach } from 'vitest';

const updatePersonMock = vi.fn();

vi.mock('$lib/server/operations', () => ({
  updatePerson: (...args: unknown[]) => updatePersonMock(...args),
  deletePerson: vi.fn(),
  getPerson: vi.fn(),
  listCountries: vi.fn().mockResolvedValue([])
}));

vi.mock('$lib/server/media', () => ({
  uploadPersonPhoto: vi.fn(),
  deletePersonPhoto: vi.fn()
}));

import { actions } from './+page.server';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function makeActionEvent(fields: Record<string, string>): any {
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) form.set(k, v);
  return {
    params: { id: 'p1' },
    request: { formData: async () => form, headers: new Headers() }
  };
}

describe('/people/[id]/edit update action', () => {
  beforeEach(() => {
    updatePersonMock.mockReset();
  });

  it('rejects a malformed birth date before calling GraphQL, naming the value', async () => {
    const result = (await actions.update(
      makeActionEvent({ name: 'Jane Doe', birthDate: '3/3/52452242', expectedVersion: '1' })
    )) as any;
    expect(updatePersonMock).not.toHaveBeenCalled();
    expect(result.status).toBe(400);
    expect(result.data.fieldErrors.birthDate).toContain('3/3/52452242');
  });

  it('rejects a tampered/non-integer expectedVersion instead of sending NaN', async () => {
    const result = (await actions.update(
      makeActionEvent({ name: 'Jane Doe', expectedVersion: 'not-a-number' })
    )) as any;
    expect(updatePersonMock).not.toHaveBeenCalled();
    expect(result.status).toBe(400);
  });

  it('passes valid fields through to the mutation with the parsed version', async () => {
    updatePersonMock.mockResolvedValue({});
    const result = (await actions.update(
      makeActionEvent({ name: 'Jane Doe', birthDate: '1980-01-01', expectedVersion: '2' })
    )) as any;
    expect(updatePersonMock).toHaveBeenCalledWith(
      'p1',
      2,
      expect.objectContaining({ birthDate: '1980-01-01' }),
      expect.anything()
    );
    expect(result).toEqual({ updated: true });
  });

  it('passes birthCountryCode through to the mutation', async () => {
    updatePersonMock.mockResolvedValue({});
    await actions.update(
      makeActionEvent({ name: 'Jane Doe', birthCountryCode: 'FR', expectedVersion: '2' })
    );
    expect(updatePersonMock).toHaveBeenCalledWith(
      'p1',
      2,
      expect.objectContaining({ birthCountryCode: 'FR' }),
      expect.anything()
    );
  });

  it('only changed fields are sent in the update mask (F21)', async () => {
    updatePersonMock.mockResolvedValue({});
    await actions.update(
      makeActionEvent({
        expectedVersion: '3',
        'base.name': 'Jane Doe',
        name: 'Jane Doe', // unchanged
        'base.biography': 'Old bio.',
        biography: 'New bio.' // changed
      })
    );
    expect(updatePersonMock).toHaveBeenCalledWith('p1', 3, { biography: 'New bio.' }, expect.anything());
  });

  it('a no-op submit (nothing changed) skips the mutation entirely', async () => {
    const result = (await actions.update(
      makeActionEvent({ expectedVersion: '3', 'base.name': 'Jane Doe', name: 'Jane Doe' })
    )) as any;
    expect(updatePersonMock).not.toHaveBeenCalled();
    expect(result).toEqual({ updated: true });
  });

  it('returns values on a backend failure so the form can re-render what the user typed (F16/F22)', async () => {
    const { GraphQlRequestError } = await import('$lib/server/graphql');
    updatePersonMock.mockRejectedValue(new GraphQlRequestError('CONFLICT', 'stale version', 'cid'));
    const result = (await actions.update(
      makeActionEvent({ expectedVersion: '3', 'base.name': 'Old Name', name: 'New Name' })
    )) as any;
    expect(result.status).toBe(409);
    expect(result.data.values).toEqual(expect.objectContaining({ name: 'New Name' }));
  });
});
