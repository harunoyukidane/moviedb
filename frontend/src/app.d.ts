// See https://kit.svelte.dev/docs/types#app
declare global {
  namespace App {
    interface Error {
      code?: string;
      message: string;
    }
    // interface Locals {}
    // interface PageData {}
    // interface Platform {}
  }
}

export {};
