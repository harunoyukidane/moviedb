const PASSTHROUGH_HEADERS = [
  'content-type',
  'content-length',
  'etag',
  'cache-control',
  'x-content-type-options',
  'vary'
] as const;

/** Relay a cacheable media GET through the BFF without buffering the body. */
export async function proxyMediaGet(upstream: string, request: Request): Promise<Response> {
  const headers: Record<string, string> = {};
  const ifNoneMatch = request.headers.get('if-none-match');
  if (ifNoneMatch) headers['If-None-Match'] = ifNoneMatch;
  // Forwarded so the media service's Accept-negotiated WebP variant (§ ArtworkServingController /
  // PersonPhotoController) actually reaches the browser instead of always falling back to the
  // primary format - the upstream fetch here would otherwise carry no Accept header of its own.
  const accept = request.headers.get('accept');
  if (accept) headers['Accept'] = accept;

  let response: Response;
  try {
    response = await fetch(upstream, { headers });
  } catch {
    return new Response(null, { status: 502 });
  }

  const responseHeaders: Record<string, string> = {};
  for (const name of PASSTHROUGH_HEADERS) {
    const value = response.headers.get(name);
    if (value) responseHeaders[name] = value;
  }

  if (response.status === 304) return new Response(null, { status: 304, headers: responseHeaders });
  if (!response.ok) return new Response(null, { status: response.status });
  return new Response(response.body, { status: response.status, headers: responseHeaders });
}
