export interface JsonResponseInit extends ResponseInit {
  headers?: HeadersInit;
}

export function json(data: unknown, init: JsonResponseInit = {}): Response {
  const headers = new Headers(init.headers);

  if (!headers.has("content-type")) {
    headers.set("content-type", "application/json; charset=utf-8");
  }

  return new Response(JSON.stringify(data), {
    ...init,
    headers,
  });
}
