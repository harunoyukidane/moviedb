// Person chaos scenario — same shape as movies.ts: a small shared pool of
// synthetic, title-prefixed people gets hammered with concurrent
// create/update/delete/photo-upload so the same person is amended and
// deleted from multiple workers at once.

import type { CatalogueGraphqlClient } from '../graphqlClient.js';
import type { Stats } from '../stats.js';
import { attempt, nextTag, pick } from '../util.js';
import { uploadPersonPhoto } from '../restClient.js';

export interface PersonSlot {
  id?: string;
  version?: number;
}

export function makePersonSlots(count: number): PersonSlot[] {
  return Array.from({ length: count }, () => ({}));
}

const BIOGRAPHIES = [
  'A synthetic record generated for concurrency load testing.',
  'Placeholder biography; this row exists only to stress-test writes.',
  'Load-test fixture — safe to overwrite, update, or delete at any time.'
];

async function createInto(
  client: CatalogueGraphqlClient,
  stats: Stats,
  slot: PersonSlot,
  createdIds: Set<string>
): Promise<void> {
  const tag = nextTag();
  const result = await attempt(stats, 'person.create', () =>
    client.createPerson({
      name: `[LOADTEST] Person ${tag}`,
      biography: pick(BIOGRAPHIES)
    })
  );
  if (result) {
    // See movies.ts createInto: record unconditionally, the slot alone can
    // miss a create that raced with another create into the same empty slot.
    createdIds.add(result.id);
    slot.id = result.id;
    slot.version = result.version;
  }
}

async function updateSlot(
  client: CatalogueGraphqlClient,
  stats: Stats,
  slot: PersonSlot,
  workerId: number
): Promise<void> {
  const targetId = slot.id;
  const targetVersion = slot.version;
  if (targetId === undefined || targetVersion === undefined) return;
  const result = await attempt(stats, 'person.update', () =>
    client.updatePerson(targetId, targetVersion, `[LOADTEST] Person edited by worker ${workerId} @ ${nextTag()}`)
  );
  if (result && slot.id === targetId) {
    slot.version = result.version;
  }
}

async function deleteSlot(client: CatalogueGraphqlClient, stats: Stats, slot: PersonSlot): Promise<void> {
  const targetId = slot.id;
  if (targetId === undefined) return;
  const result = await attempt(stats, 'person.delete', () => client.deletePerson(targetId));
  if (result !== undefined && slot.id === targetId) {
    slot.id = undefined;
    slot.version = undefined;
  }
}

async function uploadToSlot(peopleHttpUrl: string, stats: Stats, slot: PersonSlot): Promise<void> {
  const targetId = slot.id;
  if (targetId === undefined) return;
  await attempt(stats, 'person.uploadPhoto', () => uploadPersonPhoto(peopleHttpUrl, targetId));
}

export async function personWorker(
  workerId: number,
  slots: PersonSlot[],
  client: CatalogueGraphqlClient,
  peopleHttpUrl: string,
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
    // See movies.ts: never create into an occupied slot, or the previous
    // occupant is orphaned and final cleanup can't find it.
    const roll = Math.random();
    if (roll < 0.45) {
      await updateSlot(client, stats, slot, workerId);
    } else if (roll < 0.8) {
      await deleteSlot(client, stats, slot);
    } else {
      await uploadToSlot(peopleHttpUrl, stats, slot);
    }
  }
}

export async function cleanupPersonSlots(client: CatalogueGraphqlClient, createdIds: Set<string>): Promise<void> {
  await Promise.all(
    [...createdIds].map(async (id) => {
      try {
        await client.deletePerson(id);
      } catch {
        // best-effort
      }
    })
  );
}
