<script lang="ts">
  // Consistent inline-SVG pattern (V2-12): a single place mapping a short name to
  // path data sourced from frontend/src/resources/*.svg (Google Material Symbols),
  // with the hardcoded fill swapped for currentColor so it follows the caller's
  // text color/theme. Purely decorative — always aria-hidden; the accessible name
  // comes from the surrounding control (IconButton, a labeled link, etc.).
  export let name: 'delete' | 'edit' | 'view-cluster' | 'view-list';
  export let size = 20;

  const PATHS: Record<typeof name, string> = {
    delete:
      'M280-120q-33 0-56.5-23.5T200-200v-520h-40v-80h200v-40h240v40h200v80h-40v520q0 33-23.5 56.5T680-120H280Zm400-600H280v520h400v-520ZM360-280h80v-360h-80v360Zm160 0h80v-360h-80v360ZM280-720v520-520Z',
    edit: 'M200-200h57l391-391-57-57-391 391v57Zm-80 80v-170l528-527q12-11 26.5-17t30.5-6q16 0 31 6t26 18l55 56q12 11 17.5 26t5.5 30q0 16-5.5 30.5T817-647L290-120H120Zm640-584-56-56 56 56Zm-141 85-28-29 57 57-29-28Z',
    'view-cluster':
      'M340-540H200q-33 0-56.5-23.5T120-620v-140q0-33 23.5-56.5T200-840h140q33 0 56.5 23.5T420-760v140q0 33-23.5 56.5T340-540Zm-140-80h140v-140H200v140Zm140 500H200q-33 0-56.5-23.5T120-200v-140q0-33 23.5-56.5T200-420h140q33 0 56.5 23.5T420-340v140q0 33-23.5 56.5T340-120Zm-140-80h140v-140H200v140Zm560-340H620q-33 0-56.5-23.5T540-620v-140q0-33 23.5-56.5T620-840h140q33 0 56.5 23.5T840-760v140q0 33-23.5 56.5T760-540Zm-140-80h140v-140H620v140Zm140 500H620q-33 0-56.5-23.5T540-200v-140q0-33 23.5-56.5T620-420h140q33 0 56.5 23.5T840-340v140q0 33-23.5 56.5T760-120Zm-140-80h140v-140H620v140ZM340-620Zm0 280Zm280-280Zm0 280Z',
    'view-list':
      'M360-240h440v-107H360v107ZM160-613h120v-107H160v107Zm0 187h120v-107H160v107Zm0 186h120v-107H160v107Zm200-186h440v-107H360v107Zm0-187h440v-107H360v107ZM160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h640q33 0 56.5 23.5T880-720v480q0 33-23.5 56.5T800-160H160Z',
  };
</script>

<svg viewBox="0 -960 960 960" width={size} height={size} fill="currentColor" aria-hidden="true">
  <path d={PATHS[name]} />
</svg>
