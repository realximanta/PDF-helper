/* ============================================================
   PDF Helper — landing page
   No dependencies. No analytics. No cookies.
   ============================================================ */

(function () {
  'use strict';

  // ----------------------------------------------------------
  // Footer year
  // ----------------------------------------------------------
  const yearEl = document.getElementById('year');
  if (yearEl) yearEl.textContent = new Date().getFullYear();

  // ----------------------------------------------------------
  // Reveal on scroll
  // ----------------------------------------------------------
  const revealEls = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window && revealEls.length) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { rootMargin: '0px 0px -60px 0px', threshold: 0.05 }
    );
    revealEls.forEach((el) => observer.observe(el));
  } else {
    revealEls.forEach((el) => el.classList.add('is-visible'));
  }

  // ----------------------------------------------------------
  // Latest release metadata
  //   Source: web/data/latest.json — written by CI on every release.
  //   Shape: { version, tag, apk_url, published_at, release_notes_url }
  // ----------------------------------------------------------
  const FALLBACK_URL = 'https://github.com/realximanta/PDF-helper/releases/latest';
  const FALLBACK_NOTES = 'https://github.com/realximanta/PDF-helper/releases';

  const downloadApk = document.getElementById('download-apk');
  const downloadApk2 = document.getElementById('download-apk-2');
  const downloadLabel = document.getElementById('download-label');
  const releaseNotes = document.getElementById('release-notes');
  const releaseLine = document.getElementById('release-line');
  const badgeVersion = document.getElementById('badge-version');

  function formatDate(iso) {
    if (!iso) return null;
    try {
      const d = new Date(iso);
      if (isNaN(d.getTime())) return null;
      return d.toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    } catch (_) {
      return null;
    }
  }

  function applyRelease(data) {
    const version = data.version || data.tag || 'latest';
    const apkUrl = data.apk_url || FALLBACK_URL;
    const notesUrl = data.release_notes_url || FALLBACK_NOTES;

    if (downloadApk) downloadApk.href = apkUrl;
    if (downloadApk2) downloadApk2.href = apkUrl;
    if (downloadLabel) downloadLabel.textContent = 'Download APK ' + version;
    if (releaseNotes) releaseNotes.href = notesUrl;
    if (badgeVersion) badgeVersion.textContent = version;

    if (releaseLine) {
      const date = formatDate(data.published_at);
      const parts = [];
      parts.push(version);
      if (date) parts.push(date);
      parts.push('MIT licensed');
      releaseLine.textContent = parts.join('  ·  ');
    }
  }

  function showFallback() {
    if (releaseLine) {
      releaseLine.textContent =
        'Latest release  ·  See github.com/realximanta/PDF-helper/releases';
    }
    if (downloadApk) downloadApk.href = FALLBACK_URL;
    if (downloadApk2) downloadApk2.href = FALLBACK_URL;
    if (releaseNotes) releaseNotes.href = FALLBACK_NOTES;
  }

  if (releaseLine || downloadApk) {
    fetch('data/latest.json', { cache: 'no-store' })
      .then((res) => {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(applyRelease)
      .catch(showFallback);
  }
})();
