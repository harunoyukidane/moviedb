<script lang="ts">
  import type { MovieComment } from '$lib/server/types';
  import { formatDateTime } from '$lib/format';
  import StateBanner from '$lib/components/StateBanner.svelte';

  // Rendered in the given order without re-sorting: the Catalogue GraphQL API
  // already returns comments ordered reverse-chronologically (createdAt DESC,
  // id tie-break, §8.1) — re-sorting here would risk silently diverging from
  // that deterministic, tested ordering.
  export let comments: MovieComment[] = [];
  export let emptyMessage = 'No comments yet. Be the first to leave one.';
</script>

{#if comments.length === 0}
  <StateBanner variant="info">{emptyMessage}</StateBanner>
{:else}
  <ul class="comment-list" aria-label="Comments">
    {#each comments as c (c.id)}
      <li>
        <div class="comment-head">
          <span class="author">{c.authorDisplayName}</span>
          <time datetime={c.createdAt}>{formatDateTime(c.createdAt)}</time>
        </div>
        <p class="text">{c.text}</p>
      </li>
    {/each}
  </ul>
{/if}

<style>
  .comment-list { list-style: none; margin: 0; padding: 0; }
  .comment-list li { padding: var(--sp-1) 0; border-bottom: 1px solid var(--border); }
  .comment-list li:last-child { border-bottom: none; }
  .comment-head { display: flex; justify-content: space-between; align-items: baseline; gap: var(--sp-2); }
  .author { font-weight: 600; }
  time { color: var(--text-muted); font-size: 0.875rem; white-space: nowrap; }
  .text { margin: 4px 0 0; white-space: pre-wrap; }
</style>
