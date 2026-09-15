import { describe, expect, it, vi } from 'vitest';

// Regression coverage for V2-10: the movie detail query must stay a single
// GraphQL document requesting cast/creators + person in one shot (the backend
// batches person hydration into one gRPC call per request via DataLoader,
// §8.1). Adding a second query/field per credit for photos would defeat that;
// person photos are same-origin proxy URLs built from `person.id` instead.
const requestSpy = vi.fn(async (_document: string, _variables?: unknown) => ({ movie: null }));
vi.mock('graphql-request', () => ({
  GraphQLClient: class {
    request = requestSpy;
  },
  ClientError: class extends Error {}
}));

describe('getMovie', () => {
  it('issues exactly one GraphQL request for the whole movie detail projection', async () => {
    const { getMovie } = await import('./operations');
    await getMovie('m1');

    expect(requestSpy).toHaveBeenCalledTimes(1);
    const [document] = requestSpy.mock.calls[0]!;
    expect(document).toContain('cast {');
    expect(document).toContain('creators {');
    // person is requested inline within the single document, not via a
    // separate per-credit query
    expect((document.match(/person \{/g) ?? []).length).toBe(2);
  });
});
