import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("api worker smoke test", () => {
  it("returns a landing page at the root route", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    const request = new Request("http://example.com/");
    const response = await fetchHandler(
      request as never,
      {} as never,
      {} as ExecutionContext,
    );

    expect(response.status).toBe(200);
    const body = await response.text();
    expect(body).toContain("Plan tasks, routines, and focused work in one place");
    expect(body).toContain("Open web app");
    expect(body).toContain("Download apps");
    expect(body).toContain("Cross-platform flow");
    expect(body).toContain("Focus sessions");
    expect(body).toContain("Event history");
    expect(body).toContain("hero hero--stacked");
    expect(body).toContain("feature-rail");
  });

  it("returns an under-construction mobile downloads page", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    const request = new Request("http://example.com/mobile");
    const response = await fetchHandler(
      request as never,
      {} as never,
      {} as ExecutionContext,
    );

    expect(response.status).toBe(200);
    const body = await response.text();
    expect(body).toContain("Mobile apps are under construction");
    expect(body).toContain("App Store");
    expect(body).toContain("Google Play");
    expect(body).toContain("href=\"/app\"");
  });

  it("rewrites /app requests into the static asset bundle", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    let seenPath = "";
    const request = new Request("http://example.com/app");
    const response = await fetchHandler(
      request as never,
      {
        ASSETS: {
          fetch(assetRequest: Request) {
            seenPath = new URL(assetRequest.url).pathname;
            return Promise.resolve(new Response("<!doctype html><title>app</title>", {
              headers: { "content-type": "text/html" },
            }));
          },
        },
      } as never,
      {} as ExecutionContext,
    );

    expect(response.status).toBe(200);
    expect(seenPath).toBe("/");
  });

  it("serves the landing page on HEAD requests", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    const response = await fetchHandler(
      new Request("http://example.com/", { method: "HEAD" }) as never,
      {} as never,
      {} as ExecutionContext,
    );

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("text/html");
  });
});
