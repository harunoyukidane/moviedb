import type { PageServerLoad, Actions } from './$types';
import { error, fail } from '@sveltejs/kit';
import { addMovieComment, getMovie, listComments, type AddMovieCommentInput } from '$lib/server/operations';
import { messageForCode, isValidationError } from '$lib/errors';
import {
  codeForError,
  fieldErrorsForError,
  messageForError,
  requestContext,
  throwPageLoadError
} from '$lib/server/request';

const COMMENTS_PAGE_SIZE = 10;

/** Only accept a non-negative integer offset; anything else falls back to 0. */
function parseCommentsOffset(url: URL): number {
  const raw = Number(url.searchParams.get('commentsOffset') ?? '0');
  return Number.isInteger(raw) && raw >= 0 ? raw : 0;
}

// Movie data itself is read-only here (mutations for title/credits/artwork live
// on the editor at /movies/[id]/edit) — but comments are an additive, unmoderated
// feature of the detail page rather than a "movie edit" concern, so `addComment`
// is a form action here.
export const load: PageServerLoad = async ({ params, request, url }) => {
  const context = requestContext(request);
  const commentsOffset = parseCommentsOffset(url);
  try {
    const movie = await getMovie(params.id, context);
    if (!movie) throw error(404, { message: messageForCode('NOT_FOUND') });
    const comments = await listComments(params.id, COMMENTS_PAGE_SIZE, commentsOffset, context).catch(() => ({
      items: [],
      total: 0,
      limit: COMMENTS_PAGE_SIZE,
      offset: commentsOffset
    }));
    return { movie, comments };
  } catch (e) {
    throwPageLoadError(e);
  }
};

export const actions: Actions = {
  addComment: async ({ params, request }) => {
    const context = requestContext(request);
    const form = await request.formData();
    const input: AddMovieCommentInput = {
      authorDisplayName: String(form.get('authorDisplayName') ?? '').trim(),
      text: String(form.get('text') ?? '').trim()
    };
    try {
      await addMovieComment(params.id, input, context);
      return { commentAdded: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(isValidationError(code) ? 400 : 503, {
        message: messageForError(e, code),
        fieldErrors: fieldErrorsForError(e),
        section: 'comment'
      });
    }
  }
};
