<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import CreditSection from '$lib/features/credits/CreditSection.svelte';
  import CommentSection from '$lib/features/comments/CommentSection.svelte';
  import IconLink from '$lib/components/IconLink.svelte';
  import StateBanner from '$lib/components/StateBanner.svelte';

  export let data: PageData;
  export let form: ActionData;

  $: movie = data.movie;
  $: comments = data.comments;
  let tab: 'cast' | 'creators' = 'cast';
  let submittingComment = false;
  let commentForm: HTMLFormElement;

  $: hasPrevComments = comments.offset > 0;
  $: hasNextComments = comments.offset + comments.limit < comments.total;
  $: prevCommentsOffset = Math.max(0, comments.offset - comments.limit);
  $: nextCommentsOffset = comments.offset + comments.limit;

  function commentsHref(offset: number): string {
    return offset > 0 ? `?commentsOffset=${offset}` : `?`;
  }
</script>

<a class="page-back" href="/movies">← All movies</a>

<div class="detail">
  <section class="artwork-section" aria-label="Artwork">
    {#if movie.artwork}
      <img class="detail-media" src={movie.artwork.url} alt={`Poster for ${movie.title}`} />
    {:else}
      <div class="detail-media detail-fallback" aria-hidden="true">🎞️</div>
    {/if}
  </section>

  <section class="info-section">
    <div class="detail-title-row">
      <h1>{movie.title}</h1>
      <IconLink icon="edit" href={`/movies/${movie.id}/edit`} label="Edit movie" />
    </div>
    {#if movie.originalTitle}<p class="original">{movie.originalTitle}</p>{/if}

    <dl class="detail-meta">
      {#if movie.releaseDate}<dt>Released</dt><dd>{movie.releaseDate}</dd>{/if}
      {#if movie.runtimeMinutes}<dt>Runtime</dt><dd>{movie.runtimeMinutes} min</dd>{/if}
      {#if movie.originalLanguage}<dt>Language</dt><dd>{movie.language?.name ?? movie.originalLanguage}</dd>{/if}
    </dl>

    {#if movie.genres.length}
      <ul class="genre-tags" aria-label="Genres">
        {#each movie.genres as g (g.code)}<li class="tag">{g.title}</li>{/each}
      </ul>
    {/if}

    {#if movie.synopsis}<p class="synopsis">{movie.synopsis}</p>{/if}

    <div class="tabs" role="tablist" aria-label="Credits">
      <button role="tab" aria-selected={tab === 'cast'} class:active={tab === 'cast'} on:click={() => (tab = 'cast')}>
        Cast ({movie.cast.length})
      </button>
      <button role="tab" aria-selected={tab === 'creators'} class:active={tab === 'creators'} on:click={() => (tab = 'creators')}>
        Creators ({movie.creators.length})
      </button>
    </div>

    {#if tab === 'cast'}
      <div role="tabpanel">
        <CreditSection credits={movie.cast} emptyMessage="No cast yet. Add credits from the editor." />
      </div>
    {:else}
      <div role="tabpanel">
        <CreditSection credits={movie.creators} emptyMessage="No creators yet. Add credits from the editor." />
      </div>
    {/if}
  </section>
</div>

<section class="comments-section" aria-label="Comments">
  <h2>Comments ({comments.total})</h2>

  {#if form?.commentAdded}
    <StateBanner variant="info">Comment added.</StateBanner>
  {/if}
  {#if form?.message && form?.section === 'comment'}
    <StateBanner variant="error">{form.message}</StateBanner>
  {/if}

  <form
    class="comment-form"
    method="POST"
    action="?/addComment"
    bind:this={commentForm}
    use:enhance={() => {
      submittingComment = true;
      return async ({ update }) => {
        await update();
        submittingComment = false;
        if (form?.commentAdded) commentForm?.reset();
      };
    }}
  >
    <div class="field">
      <label for="authorDisplayName">Your name</label>
      <input id="authorDisplayName" name="authorDisplayName" maxlength="50" required />
    </div>
    <div class="field">
      <label for="text">Comment</label>
      <textarea id="text" name="text" rows="3" maxlength="2000" required></textarea>
    </div>
    <button type="submit" class="primary" disabled={submittingComment}>
      {submittingComment ? 'Posting…' : 'Post comment'}
    </button>
  </form>

  <CommentSection comments={comments.items} />

  {#if comments.items.length > 0 && (hasPrevComments || hasNextComments)}
    <nav class="pager" aria-label="Comments pagination">
      {#if hasPrevComments}<a href={commentsHref(prevCommentsOffset)}>← Newer</a>{/if}
      <span class="count">
        {comments.offset + 1}–{Math.min(comments.offset + comments.limit, comments.total)} of {comments.total}
      </span>
      {#if hasNextComments}<a href={commentsHref(nextCommentsOffset)}>Older →</a>{/if}
    </nav>
  {/if}
</section>

<style>
  .detail { --detail-col-width: 280px; }
  .detail-media { aspect-ratio: 2 / 3; }
  .original { color: var(--text-muted); margin-top: 0; }
  .genre-tags { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: var(--sp-1); margin: var(--sp-2) 0; }
  .tag { background: var(--surface-2); border: 1px solid var(--border); border-radius: 999px; padding: 2px var(--sp-1); font-size: 0.875rem; }
  .tabs { display: flex; gap: var(--sp-1); margin: var(--sp-2) 0; }
  .tabs button.active { background: var(--accent); color: var(--accent-contrast); border-color: var(--accent); }

  .comments-section { margin-top: var(--sp-3); border: 1px solid var(--border); border-radius: var(--radius); padding: var(--sp-2); }
  .comments-section h2 { margin-top: 0; }
  .comment-form { display: flex; flex-direction: column; gap: var(--sp-1); margin-bottom: var(--sp-3); max-width: 480px; }
  .field { display: flex; flex-direction: column; gap: 4px; }
  .pager { margin-top: var(--sp-2); }
</style>
