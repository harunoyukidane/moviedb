import { writable } from 'svelte/store';

/**
 * The pathname+search of the page the user was on immediately before the
 * current one, tracked client-side only via `afterNavigate` in the root
 * layout (V2.8-06). Null on a fresh session (direct link, bookmark, hard
 * refresh) - `BackLink` falls back to a fixed destination in that case.
 */
export const previousPageUrl = writable<string | null>(null);
