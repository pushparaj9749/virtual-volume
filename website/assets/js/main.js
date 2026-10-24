import { GestureEngine, PreviewVolume } from './gesture-engine.js';

const root = document.documentElement;
const themeToggle = document.querySelector('#theme-toggle');
function renderThemeButton() {
  const light = root.dataset.theme === 'light';
  themeToggle.setAttribute('aria-label', `Switch to ${light ? 'dark' : 'light'} theme`);
  themeToggle.setAttribute('aria-pressed', String(light));
  document.querySelector('meta[name="theme-color"]').content = light ? '#f8f9f3' : '#101514';
}
renderThemeButton();
themeToggle.addEventListener('click', () => {
  root.dataset.theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
  try { localStorage.setItem('virtual-volume-theme', root.dataset.theme); } catch { /* Optional persistence. */ }
  renderThemeButton();
});

const menu = document.querySelector('#main-nav');
const menuToggle = document.querySelector('#menu-toggle');
function closeMenu() { menu.classList.remove('is-open'); menuToggle.setAttribute('aria-expanded', 'false'); menuToggle.setAttribute('aria-label', 'Open navigation'); }
menuToggle.addEventListener('click', () => {
  const open = menu.classList.toggle('is-open');
  menuToggle.setAttribute('aria-expanded', String(open));
  menuToggle.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
});
menu.querySelectorAll('a').forEach(link => link.addEventListener('click', closeMenu));
document.addEventListener('keydown', event => { if (event.key === 'Escape') { closeMenu(); } });

const reveals = document.querySelectorAll('.reveal');
if ('IntersectionObserver' in window) {
  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => { if (entry.isIntersecting) { entry.target.classList.add('is-visible'); observer.unobserve(entry.target); } });
  }, { threshold: .05 });
  reveals.forEach(element => observer.observe(element));
} else reveals.forEach(element => element.classList.add('is-visible'));

const preview = new PreviewVolume();
const surfaces = [...document.querySelectorAll('.floating-control')];
const status = document.querySelector('#demo-status');
preview.subscribe(level => {
  const percent = Math.round(level / preview.max * 100);
  document.querySelectorAll('[data-volume-percent]').forEach(output => {
    output.replaceChildren(document.createTextNode(String(percent)));
    const suffix = document.createElement('span'); suffix.textContent = '%'; output.append(suffix);
  });
  document.querySelectorAll('[data-volume-meter]').forEach(meter => { meter.style.width = `${percent}%`; });
  document.querySelectorAll('[data-control-fill]').forEach(fill => { fill.style.height = `${Math.max(2, percent)}%`; });
  document.querySelector('#demo-level').textContent = `${level} / ${preview.max} levels`;
  surfaces.forEach(surface => { surface.setAttribute('aria-valuenow', String(level)); surface.setAttribute('aria-valuetext', `${percent} percent, simulated media volume`); });
});

surfaces.forEach(surface => {
  let pointer = null;
  let downX = 0;
  const pointers = new Set();
  const engine = new GestureEngine(() => ({ slop: 10, pixelsPerStep: Math.max(6, surface.querySelector('.control-track').clientHeight / preview.max), split: surface.getBoundingClientRect().height / 2 }), {
    start() { surface.classList.add('is-active'); },
    step(delta, tap) { preview.step(delta); surface.dataset.lastGesture = tap ? 'tap' : 'drag'; },
    end() { surface.classList.remove('is-active'); status.textContent = `${Math.round(preview.value / preview.max * 100)}% in this preview. On Android, the same gesture changes real media volume.`; }
  });
  surface.addEventListener('pointerdown', event => {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    pointers.add(event.pointerId);
    if (pointers.size > 1) { engine.cancel(); pointer = null; return; }
    pointer = event.pointerId;
    downX = event.clientX;
    engine.down(event.clientY, event.timeStamp);
    surface.setPointerCapture(event.pointerId);
    event.preventDefault();
  });
  surface.addEventListener('pointermove', event => {
    if (pointer !== event.pointerId) return;
    if (engine.phase === 'pending' && Math.abs(event.clientX - downX) > 16 && Math.abs(event.clientX - downX) > Math.abs(event.clientY - engine.downY) * 1.5) { engine.cancel(); pointer = null; return; }
    engine.move(event.clientY, event.timeStamp);
  });
  function finish(event, cancelled) {
    pointers.delete(event.pointerId);
    if (event.pointerId !== pointer) return;
    if (cancelled) engine.cancel();
    else engine.up(event.clientY, event.timeStamp, event.clientY - surface.getBoundingClientRect().top);
    pointer = null;
    if (surface.hasPointerCapture(event.pointerId)) surface.releasePointerCapture(event.pointerId);
  }
  surface.addEventListener('pointerup', event => finish(event, false));
  surface.addEventListener('pointercancel', event => finish(event, true));
  surface.addEventListener('lostpointercapture', () => { engine.cancel(); pointer = null; });
  surface.addEventListener('keydown', event => {
    const keys = { ArrowUp: 1, ArrowRight: 1, ArrowDown: -1, ArrowLeft: -1 };
    if (event.key in keys) preview.step(keys[event.key]);
    else if (event.key === 'Home') preview.set(0);
    else if (event.key === 'End') preview.set(preview.max);
    else return;
    event.preventDefault();
    status.textContent = `${preview.value} of ${preview.max} levels in the browser preview.`;
  });
});

const orientationDescriptions = {
  0: 'Portrait: the control is on the right edge.',
  90: 'Landscape left: the same physical edge is now the top edge.',
  270: 'Landscape right: the same physical edge is now the bottom edge.'
};
document.querySelectorAll('[data-rotation]').forEach(button => button.addEventListener('click', () => {
  document.querySelector('#rotation-phone').style.transform = `rotate(-${button.dataset.rotation}deg)`;
  document.querySelectorAll('[data-rotation]').forEach(option => option.setAttribute('aria-pressed', String(option === button)));
  document.querySelector('#rotation-status').textContent = orientationDescriptions[button.dataset.rotation];
}));
