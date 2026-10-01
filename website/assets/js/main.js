/* Virtual Volume — interactions
   Theme toggle, scroll reveals and the two gesture demos.
   The demo gesture logic mirrors the Android engine: dead zone first, a per-event
   step cap, and reversal that re-anchors instead of unwinding. */
(function () {
  'use strict';

  /* ---------- theme ---------- */

  var root = document.documentElement;
  var toggle = document.getElementById('theme-toggle');
  var stored = null;
  try {
    stored = window.localStorage.getItem('vv-theme');
  } catch (e) {
    stored = null;
  }
  if (stored === 'light' || stored === 'dark') {
    root.setAttribute('data-theme', stored);
  }
  if (toggle) {
    toggle.addEventListener('click', function () {
      var next = root.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
      root.setAttribute('data-theme', next);
      try {
        window.localStorage.setItem('vv-theme', next);
      } catch (e) {
        /* storage blocked; the choice still applies for this visit */
      }
    });
  }

  /* ---------- scroll reveals ---------- */

  var reveals = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window && reveals.length) {
    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.08 }
    );
    reveals.forEach(function (el) {
      observer.observe(el);
    });
  } else {
    reveals.forEach(function (el) {
      el.classList.add('is-visible');
    });
  }

  /* ---------- shared gesture simulation ---------- */

  /**
   * @param {object} options
   * @param {HTMLElement} options.surface  element that receives pointer events
   * @param {HTMLElement} options.fill     element whose height represents the level
   * @param {number} options.max           maximum level
   * @param {number} options.initial       starting level
   * @param {function(number, string):void} options.onChange  (level, reason)
   */
  function attachVolumeGesture(options) {
    var surface = options.surface;
    var fill = options.fill;
    var max = options.max;
    var level = options.initial;
    var onChange = options.onChange || function () {};

    var SLOP = 10;
    var TAP_TIMEOUT = 260;
    var MAX_STEPS_PER_EVENT = 3;

    var pointerId = null;
    var downY = 0;
    var anchorY = 0;
    var downAt = 0;
    var committed = 0;
    var direction = 0;
    var dragging = false;

    function pixelsPerStep() {
      var track = surface.clientHeight * 0.78;
      return Math.max(track / max, 6);
    }

    function render(reason) {
      var fraction = max > 0 ? level / max : 0;
      fill.style.height = Math.max(fraction * 100, 4) + '%';
      onChange(level, reason);
    }

    function apply(delta, reason) {
      if (!delta) return;
      level = Math.min(max, Math.max(0, level + delta));
      render(reason);
    }

    function setActive(active) {
      surface.classList.toggle('is-active', active);
    }

    function stepsFor(raw) {
      var dir = raw > 0 ? 1 : raw < 0 ? -1 : 0;
      if (dir !== 0 && direction !== 0 && dir !== direction) {
        direction = dir;
        committed = 0;
        anchorY = null;
        return 0;
      }
      if (dir !== 0) direction = dir;
      if (anchorY === null) return 0;
      var desired = Math.trunc(raw / pixelsPerStep());
      var delta = desired - committed;
      delta = Math.max(-MAX_STEPS_PER_EVENT, Math.min(MAX_STEPS_PER_EVENT, delta));
      if (delta !== 0) committed += delta;
      return delta;
    }

    surface.addEventListener('pointerdown', function (event) {
      if (pointerId !== null) return;
      pointerId = event.pointerId;
      downY = event.clientY;
      anchorY = event.clientY;
      downAt = event.timeStamp;
      committed = 0;
      direction = 0;
      dragging = false;
      setActive(true);
      try {
        surface.setPointerCapture(pointerId);
      } catch (e) {
        /* capture is a nicety, not a requirement */
      }
    });

    surface.addEventListener('pointermove', function (event) {
      if (pointerId !== event.pointerId) return;
      var fromDown = downY - event.clientY;

      if (!dragging) {
        if (Math.abs(fromDown) < SLOP) return;
        dragging = true;
        var sign = fromDown > 0 ? 1 : -1;
        direction = sign;
        committed = 0;
        anchorY = downY - sign * SLOP;
      }
      apply(stepsFor(anchorY - event.clientY), 'drag');
    });

    function finish(event, cancelled) {
      if (pointerId !== event.pointerId) return;
      if (!cancelled) {
        if (!dragging) {
          if (event.timeStamp - downAt <= TAP_TIMEOUT) {
            var rect = surface.getBoundingClientRect();
            var inUpperHalf = event.clientY - rect.top <= rect.height / 2;
            apply(inUpperHalf ? 1 : -1, 'tap');
          }
        } else {
          apply(stepsFor(anchorY - event.clientY), 'drag');
        }
      }
      pointerId = null;
      setActive(false);
    }

    surface.addEventListener('pointerup', function (event) {
      finish(event, false);
    });
    surface.addEventListener('pointercancel', function (event) {
      finish(event, true);
    });

    surface.addEventListener('keydown', function (event) {
      var step = 0;
      switch (event.key) {
        case 'ArrowUp':
        case 'ArrowRight':
          step = 1;
          break;
        case 'ArrowDown':
        case 'ArrowLeft':
          step = -1;
          break;
        case 'Home':
          level = 0;
          render('key');
          event.preventDefault();
          return;
        case 'End':
          level = max;
          render('key');
          event.preventDefault();
          return;
        default:
          return;
      }
      apply(step, 'key');
      event.preventDefault();
    });

    render('init');
  }

  /* ---------- hero mockup ---------- */

  var heroBar = document.getElementById('hero-bar');
  var heroFill = document.getElementById('hero-fill');
  if (heroBar && heroFill) {
    var heroLevel = 8;
    var heroMax = 15;
    heroFill.style.height = (heroLevel / heroMax) * 78 + '%';

    attachVolumeGesture({
      surface: heroBar,
      fill: heroFill,
      max: heroMax,
      initial: heroLevel,
      onChange: function (level) {
        heroFill.style.height = Math.max((level / heroMax) * 78, 4) + '%';
      }
    });
  }

  /* ---------- gesture demonstration ---------- */

  var demo = document.getElementById('demo-control');
  var demoFill = document.getElementById('demo-fill');
  var demoValue = document.getElementById('demo-value');
  var demoMeter = document.getElementById('demo-meter');
  var demoStatus = document.getElementById('demo-status');

  if (demo && demoFill) {
    var demoMax = 15;

    attachVolumeGesture({
      surface: demo,
      fill: demoFill,
      max: demoMax,
      initial: 8,
      onChange: function (level, reason) {
        if (demoValue) {
          demoValue.innerHTML = level + '<span>/ ' + demoMax + '</span>';
        }
        if (demoMeter) {
          demoMeter.style.width = (level / demoMax) * 100 + '%';
        }
        demo.setAttribute('aria-valuenow', String(level));
        if (demoStatus && reason !== 'init') {
          demoStatus.textContent =
            reason === 'tap'
              ? 'Tap applied — one level at a time.'
              : reason === 'key'
              ? 'Keyboard control — arrow keys move one level.'
              : 'Dragging — the level follows your finger.';
        }
      }
    });

    if (demoFill) {
      demoFill.style.height = (8 / demoMax) * 78 + '%';
    }
    if (demoMeter) {
      demoMeter.style.width = (8 / demoMax) * 100 + '%';
    }
  }
})();
