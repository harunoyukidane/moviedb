<script lang="ts">
  import type { LanguageCode } from '$lib/server/types';
  import CodeCombobox from './CodeCombobox.svelte';

  export let languages: LanguageCode[] = [];
  export let selected: string | null | undefined = null;
  export let name = 'originalLanguage';
  export let id = 'originalLanguage';
  export let label = 'Original language';
</script>

<CodeCombobox items={languages} {selected} {name} {id} {label} placeholder="Search languages…" />
