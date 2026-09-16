<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import IconLink from '$lib/components/IconLink.svelte';

  export let data: PageData;
  export let form: ActionData;

  $: person = data.person;
  let photoError = false;
</script>

<a class="page-back" href="/people">← All people</a>

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
    <div class="detail-title-row">
      <h1>{person.name}</h1>
      <IconLink icon="edit" href={`/people/${person.id}/edit`} label="Edit person" />
    </div>

    {#if form?.message}
      <StateBanner variant="error">{form.message}</StateBanner>
    {/if}

    <dl class="detail-meta">
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

<style>
  .detail { display: grid; grid-template-columns: 1fr; gap: var(--sp-3); }
  @media (min-width: 720px) { .detail { grid-template-columns: 220px 1fr; } }
  .photo-section img, .photo-fallback { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: var(--radius); }
  .photo-fallback { display: grid; place-items: center; font-size: 3rem; background: var(--surface-2); }
  .edit-link { display: inline-block; margin-top: var(--sp-1); }
  .credits { list-style: none; padding: 0; }
  .credits li { padding: var(--sp-1) 0; border-bottom: 1px solid var(--border); }
</style>
