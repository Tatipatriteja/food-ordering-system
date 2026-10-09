'use strict';
/* Helpers shared by the storefront (index.html) and the admin page (admin.html). Loaded first. */

const $ = (id) => document.getElementById(id);
const money = (n) => '₹' + Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
/** Escapes text before it is put into innerHTML (prevents HTML injection). */
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const percent = (rate) => `${Math.round(Number(rate) * 10000) / 100}%`;

function formatDate(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

const statusBadge = (status) => `<span class="status-badge status-${esc(status)}">${esc(status)}</span>`;

/* ---------- toast ---------- */
let toastTimer;
function toast(message, isError = false) {
  const box = $('toastMessage');
  $('toastText').textContent = message;
  $('toastIcon').className = `bi ${isError ? 'bi-exclamation-circle-fill' : 'bi-check-circle-fill'} me-2`;
  box.className = `toast-message shadow-lg show${isError ? ' error' : ''}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => box.classList.remove('show'), 3200);
}

/* ---------- API client ---------- */
/** fetch() wrapper: returns parsed JSON, throws Error(message) using the server's JSON error body. */
async function api(url, options = {}) {
  let response;
  try { response = await fetch(url, options); }
  catch (_) { throw new Error('Cannot connect to the server. Make sure the Spring Boot app is running.'); }
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw new Error((data && data.message) || `Request failed (${response.status})`);
  return data;
}
const jsonRequest = (method, body) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

/* ---------- theme (also switches Bootstrap's own dark mode) ---------- */
function applyTheme(dark) {
  document.body.dataset.theme = dark ? 'dark' : 'light';
  document.documentElement.setAttribute('data-bs-theme', dark ? 'dark' : 'light');
  const button = $('themeBtn');
  if (button) {
    button.innerHTML = `<i class="bi ${dark ? 'bi-sun' : 'bi-moon-stars'}"></i>`;
    button.setAttribute('aria-label', dark ? 'Switch to light mode' : 'Switch to dark mode');
  }
}
function initTheme() {
  let dark = false;
  try { dark = localStorage.getItem('foodie-theme') === 'dark'; } catch (_) { /* optional preference */ }
  applyTheme(dark);
  const button = $('themeBtn');
  if (button) {
    button.addEventListener('click', () => {
      const next = document.body.dataset.theme !== 'dark';
      applyTheme(next);
      try { localStorage.setItem('foodie-theme', next ? 'dark' : 'light'); } catch (_) { /* optional preference */ }
    });
  }
  const close = $('toastClose');
  if (close) close.addEventListener('click', () => $('toastMessage').classList.remove('show'));
}
initTheme();
