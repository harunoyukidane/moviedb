import type { PageServerLoad } from './$types';
import { listPeople, personNameOffset } from '$lib/server/operations';
import { messageForCode } from '$lib/errors';
import { codeForError, requestContext } from '$lib/server/request';

// 24 divides evenly by the photo-grid's column counts (up to 6 per row),
// so the last row rarely ends up partially filled.
const PAGE_SIZE = 24;

// Mirrors the People Service's PersonRules.QUERY_MAX_LEN so a long paste is
// clamped client-side instead of round-tripping to a validation error.
const QUERY_MAX_LEN = 100;

/** Trim and clamp the raw ?q param; blank collapses to null (list all, matching the backend). */
function parseQuery(url: URL): string | null {
  const raw = url.searchParams.get('q')?.trim().slice(0, QUERY_MAX_LEN);
  return raw ? raw : null;
}

/** A single A-Z letter from the alphabet-jump pager, or null if absent/invalid. */
function parseLetter(url: URL): string | null {
  const raw = url.searchParams.get('letter');
  return raw && /^[A-Za-z]$/.test(raw) ? raw.toUpperCase() : null;
}

export const load: PageServerLoad = async ({ url, request }) => {
  const query = parseQuery(url);
  const letter = parseLetter(url);
  const ctx = requestContext(request);
  try {
    // A letter jump takes precedence over a raw ?offset - it recomputes the
    // offset server-side so it always lands exactly where that letter begins.
    const offset = letter
      ? await personNameOffset(letter, query, ctx)
      : Math.max(0, Number(url.searchParams.get('offset') ?? '0') || 0);
    let page = await listPeople(query, PAGE_SIZE, offset, ctx);
    // A letter past the last name (e.g. "Z" with nothing after "Y") computes an
    // offset at/beyond the total, landing on an empty page. Fall back to the
    // last page of results instead of showing nothing.
    if (page.items.length === 0 && page.total > 0 && page.offset >= page.total) {
      const lastOffset = Math.floor((page.total - 1) / PAGE_SIZE) * PAGE_SIZE;
      page = await listPeople(query, PAGE_SIZE, lastOffset, ctx);
    }
    return { page, error: null as string | null, query };
  } catch (e) {
    const code = codeForError(e);
    const offset = Math.max(0, Number(url.searchParams.get('offset') ?? '0') || 0);
    return { page: { items: [], total: 0, limit: PAGE_SIZE, offset }, error: messageForCode(code), query };
  }
};
