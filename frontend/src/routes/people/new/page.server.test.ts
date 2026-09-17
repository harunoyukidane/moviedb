import { describe, expect, it, vi, beforeEach } from 'vitest';

const createPersonMock = vi.fn();

vi.mock('$lib/server/operations', () => ({
  createPerson: (...args: unknown[]) => createPersonMock(...args)
}));

import { actions } from './+page.server';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function makeActionEvent(fields: Record<string, string>): any {
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) form.set(k, v);
  return { request: { formData: async () => form, headers: new Headers() } };
}

describe('/people/new default action', () => {
  beforeEach(() => {
    createPersonMock.mockReset();
  });

  it('rejects a malformed birth date before calling GraphQL, naming the value', async () => {
    const result = (await actions.default(
      makeActionEvent({ name: 'Jane Doe', birthDate: '3/3/52452242' })
    )) as any;
    expect(createPersonMock).not.toHaveBeenCalled();
    expect(result.status).toBe(400);
    expect(result.data.fieldErrors.birthDate).toContain('3/3/52452242');
  });

  it('rejects a malformed death date before calling GraphQL', async () => {
    const result = (await actions.default(
      makeActionEvent({ name: 'Jane Doe', deathDate: '2026-02-30' })
    )) as any;
    expect(createPersonMock).not.toHaveBeenCalled();
    expect(result.status).toBe(400);
    expect(result.data.fieldErrors.deathDate).toBeDefined();
  });

  it('passes valid dates through to the mutation', async () => {
    createPersonMock.mockResolvedValue({ id: 'p1' });
    await expect(
      actions.default(makeActionEvent({ name: 'Jane Doe', birthDate: '1980-01-01' }))
    ).rejects.toMatchObject({ status: 303 });
    expect(createPersonMock).toHaveBeenCalledWith(
      expect.objectContaining({ birthDate: '1980-01-01' }),
      expect.anything()
    );
  });
});
