function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;");
}

type SiteShellOptions = {
  description: string;
  title: string;
};

export function renderSiteDocument(
  { title, description }: SiteShellOptions,
  body: string,
): string {
  return `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${escapeHtml(title)}</title>
    <meta name="description" content="${escapeHtml(description)}" />
    <style>
      :root {
        color-scheme: light;
        --bg: #f5f1ea;
        --bg-accent: rgba(196, 149, 104, 0.18);
        --surface: rgba(255, 255, 255, 0.76);
        --surface-strong: rgba(255, 255, 255, 0.92);
        --text: #1f1a17;
        --muted: #655a52;
        --line: rgba(74, 56, 45, 0.12);
        --accent: #b0632c;
        --accent-deep: #8c4b1e;
        --shadow: 0 20px 60px rgba(72, 45, 28, 0.10);
        --radius-xl: 32px;
        --radius-lg: 22px;
        --radius-md: 16px;
        --page-width: 1120px;
      }

      * { box-sizing: border-box; }

      body {
        margin: 0;
        min-height: 100vh;
        color: var(--text);
        font-family: "Instrument Sans", "Avenir Next", "Segoe UI", sans-serif;
        background:
          radial-gradient(circle at top left, rgba(255, 255, 255, 0.8), transparent 34%),
          radial-gradient(circle at 85% 15%, var(--bg-accent), transparent 28%),
          linear-gradient(180deg, #fcfaf6 0%, var(--bg) 55%, #efe8dd 100%);
      }

      a {
        color: inherit;
        text-decoration: none;
      }

      .site-shell {
        width: min(calc(100% - 20px), var(--page-width));
        margin: 0 auto;
        padding: 16px 0 40px;
      }

      .topbar {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        flex-direction: column;
        gap: 12px;
        margin-bottom: 20px;
      }

      .brand {
        display: inline-flex;
        align-items: center;
        gap: 12px;
        font-size: 0.95rem;
        font-weight: 600;
        letter-spacing: 0.08em;
        text-transform: uppercase;
      }

      .brand-mark {
        width: 42px;
        height: 42px;
        border-radius: 14px;
        background: linear-gradient(145deg, #c97a42, #7c4a25);
        color: #fffaf4;
        display: grid;
        place-items: center;
        box-shadow: 0 10px 30px rgba(124, 74, 37, 0.25);
        font-size: 1rem;
        font-weight: 700;
      }

      .nav-links {
        display: inline-flex;
        gap: 10px;
        flex-wrap: wrap;
        color: var(--muted);
        font-size: 0.92rem;
      }

      .hero,
      .panel {
        background: var(--surface);
        border: 1px solid var(--line);
        box-shadow: var(--shadow);
        backdrop-filter: blur(12px);
      }

      .hero {
        border-radius: 24px;
        padding: 22px;
        display: grid;
        grid-template-columns: 1fr;
        gap: 18px;
        align-items: stretch;
      }

      .hero--stacked {
        gap: 20px;
      }

      .hero-body {
        display: grid;
        gap: 18px;
      }

      .eyebrow {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 8px 12px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.68);
        border: 1px solid rgba(176, 99, 44, 0.18);
        color: var(--accent-deep);
        font-size: 0.82rem;
        letter-spacing: 0.08em;
        text-transform: uppercase;
      }

      h1, h2, h3, p {
        margin: 0;
      }

      h1 {
        font-family: "Iowan Old Style", "Palatino Linotype", serif;
        font-size: clamp(2.7rem, 11vw, 5.3rem);
        line-height: 0.98;
        letter-spacing: -0.04em;
      }

      .hero-copy {
        max-width: 38rem;
        font-size: 1rem;
        line-height: 1.72;
        color: var(--muted);
      }

      .hero-actions {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }

      .button,
      .button-subtle {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        min-height: 50px;
        padding: 0 20px;
        border-radius: 999px;
        font-weight: 600;
        transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease;
      }

      .button:hover,
      .button-subtle:hover {
        transform: translateY(-1px);
      }

      .button {
        background: linear-gradient(135deg, var(--accent), var(--accent-deep));
        color: #fffaf4;
        box-shadow: 0 14px 28px rgba(176, 99, 44, 0.24);
      }

      .button-subtle {
        border: 1px solid rgba(74, 56, 45, 0.14);
        background: rgba(255, 255, 255, 0.62);
        color: var(--text);
      }

      .hero-metrics,
      .feature-grid,
      .flow-grid,
      .platform-grid,
      .download-grid {
        display: grid;
        gap: 14px;
      }

      .hero-metrics {
        grid-template-columns: 1fr;
      }

      .feature-rail {
        display: grid;
        grid-template-columns: 1fr;
        gap: 12px;
      }

      .stat-card,
      .feature-card,
      .flow-card,
      .platform-card,
      .download-card {
        border-radius: var(--radius-lg);
        background: var(--surface-strong);
        border: 1px solid rgba(74, 56, 45, 0.1);
        padding: 18px;
      }

      .stat-card strong {
        display: block;
        font-size: 1.45rem;
        margin-bottom: 6px;
        font-weight: 700;
      }

      .stat-card span,
      .feature-card p,
      .flow-card p,
      .platform-card p,
      .download-card p,
      .section-copy,
      .footer {
        color: var(--muted);
        line-height: 1.65;
      }

      section {
        margin-top: 16px;
      }

      .panel {
        border-radius: 24px;
        padding: 22px;
      }

      .section-heading {
        display: flex;
        justify-content: space-between;
        gap: 12px;
        align-items: flex-start;
        flex-direction: column;
        margin-bottom: 18px;
      }

      .section-heading h2 {
        font-family: "Iowan Old Style", "Palatino Linotype", serif;
        font-size: clamp(2rem, 7vw, 2.8rem);
        letter-spacing: -0.03em;
      }

      .feature-grid,
      .flow-grid,
      .platform-grid,
      .download-grid {
        grid-template-columns: 1fr;
      }

      .feature-card h3,
      .flow-card h3,
      .platform-card h3,
      .download-card h3 {
        margin-bottom: 10px;
        font-size: 1.08rem;
      }

      .platform-card,
      .download-card {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }

      .store-link {
        display: inline-flex;
        align-items: center;
        justify-content: space-between;
        border-radius: 14px;
        padding: 14px 16px;
        border: 1px solid rgba(74, 56, 45, 0.12);
        background: rgba(255, 255, 255, 0.58);
      }

      .store-link[aria-disabled="true"] {
        color: var(--muted);
        cursor: default;
      }

      .store-status {
        font-size: 0.88rem;
        color: var(--accent-deep);
      }

      .footer {
        padding: 12px 4px 0;
        font-size: 0.94rem;
      }

      @media (min-width: 700px) {
        .site-shell {
          width: min(calc(100% - 32px), var(--page-width));
          padding-top: 24px;
          padding-bottom: 48px;
        }

        .topbar {
          align-items: center;
          flex-direction: row;
          gap: 16px;
          margin-bottom: 28px;
        }

        .hero,
        .panel {
          border-radius: var(--radius-xl);
          padding: 32px;
        }

        .hero-metrics {
          grid-template-columns: repeat(2, minmax(0, 1fr));
        }

        .hero-actions {
          flex-direction: row;
          flex-wrap: wrap;
        }

        .button,
        .button-subtle {
          width: auto;
          min-width: 188px;
        }

        .feature-rail {
          grid-template-columns: repeat(3, minmax(0, 1fr));
        }

        h1 {
          font-size: clamp(3rem, 7vw, 4.6rem);
        }

        .feature-grid,
        .flow-grid,
        .platform-grid,
        .download-grid {
          grid-template-columns: repeat(2, minmax(0, 1fr));
        }
      }

      @media (min-width: 980px) {
        .hero--stacked {
          grid-template-columns: minmax(0, 1.08fr) minmax(280px, 0.92fr);
          gap: 28px;
        }

        .hero {
          padding: 44px;
        }

        .feature-grid,
        .flow-grid,
        .platform-grid,
        .download-grid {
          grid-template-columns: repeat(3, minmax(0, 1fr));
        }

        .section-heading {
          flex-direction: row;
          align-items: end;
          gap: 20px;
        }
      }
    </style>
  </head>
  <body>
    ${body}
  </body>
</html>`;
}
