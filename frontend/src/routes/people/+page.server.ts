import type { PageServerLoad } from './$types';
import { listPeople } from '$lib/server/operations';
import { messageForCode } from '$lib/errors';
import { codeForError, requestContext } from '$lib/server/request';

const PAGE_SIZE = 20;

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
