import { renderSiteDocument } from "./site-shell";

export function renderLandingPage(): string {
  return renderSiteDocument(
    {
      title: "Abun",
      description: "A clean planning system for tasks, routines, focus sessions, and progress across web and desktop.",
    },
    `<div class="site-shell">
      <header class="topbar">
        <a class="brand" href="/">
          <span class="brand-mark">A</span>
          <span>Abun</span>
        </a>
        <nav class="nav-links" aria-label="Primary">
          <a href="/app">Web app</a>
          <a href="/mobile">Downloads</a>
        </nav>
      </header>

      <main>
        <section class="hero hero--stacked">
          <div class="hero-body">
            <span class="eyebrow">Tasks, routines, and focus</span>
            <h1>Plan tasks, routines, and focused work in one place</h1>
            <p class="hero-copy">
              Abun brings everyday planning into one calm system. Capture what matters,
              structure recurring work, run focus sessions, and keep a clear record of progress
              across web, desktop, and the mobile experience that is on the way.
            </p>
            <div class="hero-actions">
              <a class="button" href="/app">Open web app</a>
              <a class="button-subtle" href="/mobile">Download apps</a>
            </div>
          </div>
          <div class="hero-metrics" aria-label="Highlights">
            <article class="stat-card">
              <strong>Cross-platform flow</strong>
              <span>Move between web and desktop without losing the thread of your day.</span>
            </article>
            <article class="stat-card">
              <strong>Focus sessions</strong>
              <span>Turn intent into momentum with timers that connect back to real work.</span>
            </article>
            <article class="stat-card">
              <strong>Event history</strong>
              <span>See what changed, what moved forward, and what still needs attention.</span>
            </article>
            <article class="stat-card">
              <strong>Routine structure</strong>
              <span>Keep recurring responsibilities dependable without turning your list into noise.</span>
            </article>
          </div>
        </section>

        <section class="panel">
          <div class="section-heading">
            <div>
              <h2>A planning system with shape</h2>
              <p class="section-copy">Abun is designed to feel composed instead of crowded, with features that support both daily execution and longer arcs of work.</p>
            </div>
          </div>
          <div class="feature-rail">
            <article class="feature-card">
              <h3>Tasks with momentum</h3>
              <p>Track open work, close loops cleanly, and update progress without fighting the interface.</p>
            </article>
            <article class="feature-card">
              <h3>Routines that stay useful</h3>
              <p>Build repeatable rhythms for the work that needs to reappear without becoming invisible.</p>
            </article>
            <article class="feature-card">
              <h3>Planning with context</h3>
              <p>Notes, events, and status changes stay attached to the work itself, so history remains legible.</p>
            </article>
          </div>
        </section>

        <section class="panel">
          <div class="section-heading">
            <div>
              <h2>How Abun helps you move</h2>
              <p class="section-copy">From capture to review, Abun keeps the system coherent without forcing every kind of work into the same pattern.</p>
            </div>
          </div>
          <div class="flow-grid">
            <article class="flow-card">
              <h3>Capture and shape</h3>
              <p>Add tasks, define routines, and give each item enough structure to stay actionable.</p>
            </article>
            <article class="flow-card">
              <h3>Work with intention</h3>
              <p>Use built-in focus support to move from planning into execution without context switching.</p>
            </article>
            <article class="flow-card">
              <h3>Review the trail</h3>
              <p>Check event history and status changes to understand what actually happened, not just what was planned.</p>
            </article>
          </div>
        </section>

        <section class="panel">
          <div class="section-heading">
            <div>
              <h2>Choose your way in</h2>
              <p class="section-copy">Start in the browser now, or keep an eye on the mobile rollout while the download pages take shape.</p>
            </div>
          </div>
          <div class="platform-grid">
            <article class="platform-card">
              <h3>Web app</h3>
              <p>Use Abun immediately in the browser with the full planning experience served from the same domain.</p>
              <a class="button" href="/app">Open web app</a>
            </article>
            <article class="platform-card">
              <h3>Mobile downloads</h3>
              <p>The download hub is live as a placeholder so the platform rollout has a stable home from day one.</p>
              <a class="button-subtle" href="/mobile">Go to download page</a>
            </article>
            <article class="platform-card">
              <h3>Desktop continuity</h3>
              <p>The wider Abun product also targets desktop so your planning system can stay consistent across environments.</p>
              <a class="button-subtle" href="/mobile">See platform status</a>
            </article>
          </div>
        </section>
      </main>

      <footer class="footer">
        Abun for thoughtful planning, recurring work, and visible progress.
      </footer>
    </div>`,
  );
}
