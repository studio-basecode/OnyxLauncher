const CURSEFORGE_ORIGIN = "https://api.curseforge.com";
const ALLOWED_PREFIX = "/v1/";

export default {
  async fetch(request, env) {
    if (request.method !== "GET" && request.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    const apiKey = env.CURSEFORGE_API_KEY;
    if (!apiKey) {
      return new Response("CurseForge API key is not configured", { status: 500 });
    }

    const sourceUrl = new URL(request.url);
    if (!sourceUrl.pathname.startsWith(ALLOWED_PREFIX)) {
      return new Response("Not found", { status: 404 });
    }

    const upstreamUrl = new URL(sourceUrl.pathname + sourceUrl.search, CURSEFORGE_ORIGIN);
    const headers = new Headers(request.headers);
    headers.set("x-api-key", apiKey);
    headers.set("accept", "application/json");
    headers.delete("host");
    headers.delete("cookie");

    const upstreamRequest = new Request(upstreamUrl, {
      method: request.method,
      headers,
      body: request.method === "GET" ? undefined : request.body,
      redirect: "follow",
    });

    const response = await fetch(upstreamRequest);
    const outputHeaders = new Headers(response.headers);
    outputHeaders.set("access-control-allow-origin", "*");
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: outputHeaders,
    });
  },
};
