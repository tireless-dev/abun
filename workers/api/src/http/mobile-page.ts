import { renderSiteDocument } from "./site-shell";

export function renderMobilePage(): string {
  return renderSiteDocument(
    {
      title: "Abun mobile",
      description: "Download status for the upcoming Abun mobile apps.",
    },
    `<div class="site-shell">
      <header class="topbar">
        <a class="brand" href="/">
          <span class="brand-mark">A</span>
          <span>Abun</span>
        </a>
        <nav class="nav-links" aria-label="Primary">
          <a href="/">Home</a>
          <a href="/app">Web app</a>
        </nav>
      </header>

      <main>
        <section class="hero">
          <div>
            <span class="eyebrow">Mobile rollout</span>
            <h1>Mobile apps are under construction</h1>
            <p class="hero-copy">
              The iOS and Android download pages are being prepared. This page will become the
              stable home for store links as the mobile release is finalized.
            </p>
            <div class="hero-actions">
              <a class="button" href="/app">Open web app</a>
              <a class="button-subtle" href="/">Back to home</a>
            </div>
          </div>
          <div class="download-grid">
            <article class="download-card">
              <h3>App Store</h3>
              <p>iPhone and iPad distribution details will appear here when the first release is ready.</p>
              <a class="store-link" href="" aria-disabled="true">
                <span>App Store</span>
                <span class="store-status">Coming soon</span>
              </a>
            </article>
            <article class="download-card">
              <h3>Google Play</h3>
              <p>Android availability will be published here alongside release notes and version status.</p>
              <a class="store-link" href="" aria-disabled="true">
                <span>Google Play</span>
                <span class="store-status">Coming soon</span>
              </a>
            </article>
            <article class="download-card">
              <h3>Until then</h3>
              <p>You can already use Abun in the browser while the mobile experience is being finished.</p>
              <a class="button-subtle" href="/app">Use the web app</a>
            </article>
          </div>
        </section>
      </main>

      <footer class="footer">
        Mobile links will be published here when the app stores are ready.
      </footer>
    </div>`,
  );
}
