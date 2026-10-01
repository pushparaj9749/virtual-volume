/** Pure browser counterpart of Android's relative gesture engine. This is only a demo. */
export class GestureEngine {
  constructor(config, listener) {
    this.config = config;
    this.listener = listener;
    this.phase = 'idle';
  }

  down(y, at) {
    if (!Number.isFinite(y)) return false;
    this.cancel();
    this.phase = 'pending';
    this.downY = this.lastY = y;
    this.downAt = at;
    this.remainder = this.direction = 0;
    this.listener.start?.();
    return true;
  }

  move(y, at) {
    if (this.phase === 'idle') return;
    if (!Number.isFinite(y) || at < this.downAt) return this.cancel();
    const config = this.config();
    const slop = Math.max(1, config.slop);
    if (this.phase === 'pending') {
      const distance = this.downY - y;
      if (Math.abs(distance) <= slop) return;
      this.phase = 'dragging';
      this.lastY = this.downY - Math.sign(distance) * slop;
    }
    const distance = this.lastY - y;
    this.lastY = y;
    if (distance === 0) return;
    const direction = Math.sign(distance);
    if (this.direction !== 0 && this.direction !== direction) this.remainder = 0;
    this.direction = direction;
    this.remainder += distance;
    const pixels = Number.isFinite(config.pixelsPerStep) && config.pixelsPerStep > 0 ? config.pixelsPerStep : 24;
    const desired = Math.trunc(this.remainder / pixels);
    if (desired === 0) return;
    this.remainder -= desired * pixels;
    const cap = Math.max(1, config.maxSteps ?? 3);
    this.listener.step(Math.max(-cap, Math.min(cap, desired)), false);
  }

  up(y, at, tapPosition = y) {
    if (this.phase === 'idle') return;
    this.move(y, at);
    if (this.phase === 'idle') return;
    const config = this.config();
    if (this.phase === 'pending' && at - this.downAt >= 0 && at - this.downAt <= (config.tapTimeout ?? 260)) {
      this.listener.step(tapPosition <= config.split ? 1 : -1, true);
    }
    this.finish();
  }

  cancel() { if (this.phase !== 'idle') this.finish(); }
  finish() { this.phase = 'idle'; this.remainder = this.direction = 0; this.listener.end?.(); }
}

export class PreviewVolume {
  constructor(initial = 10, max = 15) { this.max = max; this.value = initial; this.listeners = new Set(); }
  set(value) {
    if (!Number.isFinite(value)) return;
    this.value = Math.min(this.max, Math.max(0, Math.round(value)));
    this.listeners.forEach(listener => listener(this.value));
  }
  step(delta) { this.set(this.value + delta); }
  subscribe(listener) { this.listeners.add(listener); listener(this.value); return () => this.listeners.delete(listener); }
}
