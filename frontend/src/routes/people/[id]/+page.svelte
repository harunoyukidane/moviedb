<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';

  export let data: PageData;
  export let form: ActionData;

  $: person = data.person;
  let confirmDeleteOpen = false;
  let photoError = false;

  function submitDelete() {
    const f = document.getElementById('person-delete-form');
    if (f instanceof HTMLFormElement) f.requestSubmit();
  }
</script>

<a class="back" href="/people">← All people</a>

<div class="detail">
  <section class="photo-section" aria-label="Photo">
    {#if !photoError}
      <img src={data.photoUrl} alt={`Photo of ${person.name}`} on:error={() => (photoError = true)} />
    {:else}
      <div class="photo-fallback" aria-hidden="true">👤</div>
    {/if}
    <a class="edit-link" href={`/people/${person.id}/edit#photo`}>Manage photo</a>
  </section>

  <section class="info">
    <div class="title-row">
      <h1>{person.name}</h1>
      <div class="actions">
        <a class="btn" href={`/people/${person.id}/edit`}>Edit</a>
        <button type="button" class="danger" on:click={() => (confirmDeleteOpen = true)}>Delete</button>
      </div>
    </div>

    {#if form?.message}
      <StateBanner variant="error">{form.message}</StateBanner>
    {/if}

    <dl class="meta">
      {#if person.birthDate}<dt>Born</dt><dd>{person.birthDate}{person.placeOfBirth ? ` · ${person.placeOfBirth}` : ''}</dd>{/if}
      {#if person.deathDate}<dt>Died</dt><dd>{person.deathDate}</dd>{/if}
    </dl>

    {#if person.biography}<p class="bio">{person.biography}</p>{/if}

    <h2>Filmography</h2>
    {#if person.credits.length === 0}
      <StateBanner variant="info">No credits yet.</StateBanner>
    {:else}
      <ul class="credits">
        {#each person.credits as c (c.movieId + c.role.code + (c.characterName ?? ''))}
          <li>
            <a href={`/movies/${c.movieId}`}>{c.movieTitle}</a>
            — {c.category === 'CAST' ? `as ${c.characterName}` : c.role.title}
          </li>
        {/each}
      </ul>
    {/if}
  </section>
</div>

<ConfirmDialog
  bind:open={confirmDeleteOpen}
  title="Delete this person?"
  confirmLabel="Delete person"
  danger
  on:confirm={submitDelete}
>
  This removes “{person.name}” entirely. If they are still credited on any movie, the delete will be blocked
  until you remove those credits.
</ConfirmDialog>

<form id="person-delete-form" method="POST" action="?/delete" use:enhance hidden></form>

<style>
  .back { display: inline-block; margin-bottom: var(--sp-2); color: var(--text-muted); }
  .detail { display: grid; grid-template-columns: 1fr; gap: var(--sp-3); }
  @media (min-width: 720px) { .detail { grid-template-columns: 220px 1fr; } }
  .photo-section img, .photo-fallback { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: var(--radius); }
  .photo-fallback { display: grid; place-items: center; font-size: 3rem; background: var(--surface-2); }
  .edit-link { display: inline-block; margin-top: var(--sp-1); }
  .title-row { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--sp-2); }
  .actions { display: flex; gap: var(--sp-1); }
  .btn { background: var(--surface-2); border: 1px solid var(--border); color: var(--text); padding: var(--sp-1) var(--sp-2); border-radius: var(--radius); text-decoration: none; }
  .meta { display: grid; grid-template-columns: auto 1fr; gap: 4px var(--sp-2); }
  .meta dt { color: var(--text-muted); }
  .meta dd { margin: 0; }
  .credits { list-style: none; padding: 0; }
  .credits li { padding: var(--sp-1) 0; border-bottom: 1px solid var(--border); }
</style>
