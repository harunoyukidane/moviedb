<script lang="ts">
  import type { CountryCode } from '$lib/server/types';
  import CodeCombobox from './CodeCombobox.svelte';

  export let countries: CountryCode[] = [];
  export let selected: string | null | undefined = null;
  export let name = 'birthCountryCode';
  export let id = 'birthCountryCode';
  export let label = 'Country';
</script>

<CodeCombobox items={countries} {selected} {name} {id} {label} placeholder="Search countries…" />
