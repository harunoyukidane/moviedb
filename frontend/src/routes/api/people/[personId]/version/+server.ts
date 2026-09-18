import type { RequestHandler } from './$types';
import { json } from '@sveltejs/kit';
import { getPersonVersion } from '$lib/server/operations';

// BFF staleness-check endpoint (V2.2-11): the edit page polls this on window
// focus and just before submit to detect a concurrent edit early, without a
// full getPerson round trip. Never fails the page - a lookup error just means
// no staleness warning this time, not a blocked editor.
export const GET: RequestHandler = async ({ params, request }) => {
  const correlationId = request.headers.get('x-correlation-id') ?? undefined;
  try {
    const version = await getPersonVersion(params.personId, { correlationId });
    return json({ version });
  } catch {
    return json({ version: null });
  }
};
