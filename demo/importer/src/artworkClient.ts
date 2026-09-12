import type { ImporterConfig } from './config.js';
import type { ArtworkPort } from './ports.js';

/**
 * Artwork HTTP adapter (§12.3 step 8). Uploads a poster through the same
 * validated media endpoint the UI uses (PUT multipart `file`). A failed upload
 * throws so the orchestrator can record it as a non-fatal per-item outcome; the
 * movie itself remains imported (§13).
 */
export class ArtworkHttpClient implements ArtworkPort {
  constructor(
    private readonly config: ImporterConfig,
    private readonly correlationId: string,
    private readonly fetchFn: typeof fetch = fetch
  ) {}

  async uploadMoviePoster(movieId: string, bytes: Uint8Array, contentType: string): Promise<void> {
    const url = `${this.config.catalogueHttpUrl}/api/movies/${movieId}/artwork`;
    const ext = contentType.includes('png') ? 'png' : contentType.includes('webp') ? 'webp' : 'jpg';
    const form = new FormData();
    // copy into a fresh ArrayBuffer-backed Blob (avoids SharedArrayBuffer typing issues)
    const ab = new ArrayBuffer(bytes.byteLength);
    new Uint8Array(ab).set(bytes);
    form.append('file', new Blob([ab], { type: contentType }), `poster.${ext}`);

    const res = await this.fetchFn(url, {
      method: 'PUT',
      headers: { 'X-Correlation-ID': this.correlationId },
      body: form
    });
    if (!res.ok) {
      throw new Error(`poster upload failed for movie ${movieId}: HTTP ${res.status}`);
    }
  }
}
