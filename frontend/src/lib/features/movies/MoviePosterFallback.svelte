<!-- Shared placeholder for a movie with no uploaded artwork; used by both the
     cluster and list renderers (Requirement 2: "existing poster fallback placeholder"). -->
<div class="poster-fallback" aria-hidden="true">🎞️</div>

<style>
  .poster-fallback {
    width: 100%;
    aspect-ratio: 2 / 3;
    display: grid;
    place-items: center;
    font-size: 2.5rem;
    background: var(--surface-2);
  }
</style>
