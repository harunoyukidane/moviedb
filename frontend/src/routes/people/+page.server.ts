import type { PageServerLoad } from './$types';
import { listPeople } from '$lib/server/operations';
import { messageForCode } from '$lib/errors';
import { codeForError, requestContext } from '$lib/server/request';

// 24 divides evenly by the photo-grid's column counts (up to 6 per row),
// so the last row rarely ends up partially filled.
const PAGE_SIZE = 24;

export const load: PageServerLoad = async ({ url, request }) => {
  const offset = Math.max(0, Number(url.searchParams.get('offset') ?? '0') || 0);
  try {
    const page = await listPeople(null, PAGE_SIZE, offset, requestContext(request));
    return { page, error: null as string | null };
  } catch (e) {
    const code = codeForError(e);
    return { page: { items: [], total: 0, limit: PAGE_SIZE, offset }, error: messageForCode(code) };
  }
};
