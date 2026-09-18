import type { PageServerLoad } from './$types';
import { listPeople } from '$lib/server/operations';
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

export const load: PageServerLoad = async ({ url, request }) => {
  const offset = Math.max(0, Number(url.searchParams.get('offset') ?? '0') || 0);
  const query = parseQuery(url);
  try {
    const page = await listPeople(query, PAGE_SIZE, offset, requestContext(request));
    return { page, error: null as string | null, query };
  } catch (e) {
    const code = codeForError(e);
    return { page: { items: [], total: 0, limit: PAGE_SIZE, offset }, error: messageForCode(code), query };
  }
};
