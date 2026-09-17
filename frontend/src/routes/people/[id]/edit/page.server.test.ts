import { describe, expect, it, vi, beforeEach } from 'vitest';

const updatePersonMock = vi.fn();

vi.mock('$lib/server/operations', () => ({
  updatePerson: (...args: unknown[]) => updatePersonMock(...args),
  deletePerson: vi.fn(),
  getPerson: vi.fn()
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
});
