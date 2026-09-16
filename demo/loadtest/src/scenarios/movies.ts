// Movie chaos scenario: a small shared pool of "hot" movies (all synthetic,
// title-prefixed so they never touch the curated demo catalogue) is hammered
// by many concurrent workers doing create/update/delete/artwork-upload. The
// pool being small and shared is deliberate: it forces multiple workers to
// target the *same* row, producing the races the assessment asked for —
// concurrent amends on one movie, concurrent deletes of one movie, and
// amend-vs-delete races on the same movie at the same time.

import type { CatalogueGraphqlClient } from '../graphqlClient.js';
import type { Stats } from '../stats.js';
import { attempt, nextTag, pick } from '../util.js';
import { uploadMovieArtwork } from '../restClient.js';

export interface MovieSlot {
  id?: string;
  version?: number;
}

export function makeMovieSlots(count: number): MovieSlot[] {
  return Array.from({ length: count }, () => ({}));
}

const SYNOPSES = [
  'A synthetic record generated for concurrency load testing.',
  'Placeholder synopsis; this row exists only to stress-test writes.',
  'Load-test fixture — safe to overwrite, update, or delete at any time.'
];

async function createInto(
  client: CatalogueGraphqlClient,
  stats: Stats,
  slot: MovieSlot,
  createdIds: Set<string>
): Promise<void> {
  const tag = nextTag();
  const result = await attempt(stats, 'movie.create', () =>
    client.createMovie({
      title: `[LOADTEST] Movie ${tag}`,
      synopsis: pick(SYNOPSES)
    })
  );
  if (result) {
    // Record in the registry unconditionally: two workers can both observe an
    // empty slot and both create before either write lands, so the slot alone
    // is not a reliable list of everything this run has to clean up.
    createdIds.add(result.id);
    slot.id = result.id;
    slot.version = result.version;
  }
}

async function updateSlot(
  client: CatalogueGraphqlClient,
  stats: Stats,
  slot: MovieSlot,
  workerId: number
): Promise<void> {
  const targetId = slot.id;
  const targetVersion = slot.version;
  if (targetId === undefined || targetVersion === undefined) return;
  const result = await attempt(stats, 'movie.update', () =>
    client.updateMovie(targetId, targetVersion, `[LOADTEST] Movie edited by worker ${workerId} @ ${nextTag()}`)
  );
  // Only advance the slot if nobody else already replaced/deleted it under us.
  if (result && slot.id === targetId) {
    slot.version = result.version;
  }
}

async function deleteSlot(client: CatalogueGraphqlClient, stats: Stats, slot: MovieSlot): Promise<void> {
  const targetId = slot.id;
  if (targetId === undefined) return;
  const result = await attempt(stats, 'movie.delete', () => client.deleteMovie(targetId));
  if (result !== undefined && slot.id === targetId) {
    slot.id = undefined;
    slot.version = undefined;
  }
}

async function uploadToSlot(catalogueHttpUrl: string, stats: Stats, slot: MovieSlot): Promise<void> {
  const targetId = slot.id;
  if (targetId === undefined) return;
  await attempt(stats, 'movie.uploadArtwork', () => uploadMovieArtwork(catalogueHttpUrl, targetId));
}

export async function movieWorker(
  workerId: number,
  slots: MovieSlot[],
  client: CatalogueGraphqlClient,
  catalogueHttpUrl: string,
  stats: Stats,
  deadline: number,
  createdIds: Set<string>
): Promise<void> {
  while (Date.now() < deadline) {
    const slot = pick(slots);
    if (slot.id === undefined) {
      await createInto(client, stats, slot, createdIds);
      continue;
    }
    // Never create into an already-occupied slot: that would overwrite
    // slot.id with a fresh row while orphaning the previous occupant, which
    // final cleanup would then never see. A slot is only ever (re)created
    // once deleteSlot has emptied it (above).
    const roll = Math.random();
    if (roll < 0.45) {
      await updateSlot(client, stats, slot, workerId);
    } else if (roll < 0.8) {
      await deleteSlot(client, stats, slot);
    } else {
      await uploadToSlot(catalogueHttpUrl, stats, slot);
    }
  }
}

/** Best-effort cleanup so synthetic rows don't linger after the run. Sweeps
 *  the full creation registry, not just current slot state, since concurrent
 *  creates into the same just-emptied slot can orphan a slot reference. */
export async function cleanupMovieSlots(client: CatalogueGraphqlClient, createdIds: Set<string>): Promise<void> {
  await Promise.all(
    [...createdIds].map(async (id) => {
      try {
        await client.deleteMovie(id);
      } catch {
        // best-effort; already deleted by a worker, or in a state we don't care about here
      }
    })
  );
}
