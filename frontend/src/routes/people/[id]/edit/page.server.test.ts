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

import { actions, load } from './+page.server';
import { getPerson } from '$lib/server/operations';

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

  it('selecting a country only sends birthCountryCode - the rest of the profile is not wiped', async () => {
    // Regression: reported as "select a country and save, and the whole
    // profile got wiped". The mask must stay narrow to exactly the field
    // that changed (F21/V2.2-11), never widen to every field in the form.
    updatePersonMock.mockResolvedValue({});
    await actions.update(
      makeActionEvent({
        expectedVersion: '5',
        'base.name': 'Al Pacino',
        name: 'Al Pacino', // unchanged
        'base.biography': 'Legendary American actor known for The Godfather.',
        biography: 'Legendary American actor known for The Godfather.', // unchanged
        'base.birthDate': '1940-04-25',
        birthDate: '1940-04-25', // unchanged
        'base.deathDate': '',
        deathDate: '', // unchanged
        'base.placeOfBirth': 'New York City',
        placeOfBirth: 'New York City', // unchanged
        'base.birthCountryCode': '',
        birthCountryCode: 'FR' // the only real change
      })
    );
    expect(updatePersonMock).toHaveBeenCalledWith(
      'p1',
      5,
      { birthCountryCode: 'FR' },
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

describe('/people/[id]/edit load', () => {
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
