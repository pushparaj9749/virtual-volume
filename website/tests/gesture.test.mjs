import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GestureEngine, PreviewVolume } from '../assets/js/gesture-engine.js';

function harness() {
  const events = [], listener = { step: (delta, tap) => events.push({ delta, tap }), start: () => events.push('start'), end: () => events.push('end') };
  const engine = new GestureEngine(() => ({ slop: 20, pixelsPerStep: 20, split: 100, tapTimeout: 260, maxSteps: 3 }), listener);
  return { events, engine, steps: () => events.filter(event => typeof event === 'object') };
}
test('split taps raise and lower one step', () => {
  const { engine, steps } = harness(); engine.down(50, 0); engine.up(50, 80); engine.down(160, 100); engine.up(160, 180);
  assert.deepEqual(steps(), [{ delta: 1, tap: true }, { delta: -1, tap: true }]);
});
test('consumed dead zone prevents immediate volume jumps', () => {
  const { engine, steps } = harness(); engine.down(200, 0); engine.move(178, 16); assert.deepEqual(steps(), []); engine.move(120, 32); engine.up(120, 48);
  assert.deepEqual(steps(), [{ delta: 3, tap: false }]);
});
test('holding still and release cannot replay a capped fast swipe', () => {
  const { engine, steps } = harness(); engine.down(300, 0); engine.move(0, 16); for (let i = 0; i < 10; i++) engine.move(0, 32 + i); engine.up(0, 100);
  assert.deepEqual(steps(), [{ delta: 3, tap: false }]);
});
test('reversal responds immediately instead of unwinding', () => {
  const { engine, steps } = harness(); engine.down(500, 0); engine.move(200, 16); engine.move(220, 32);
  assert.deepEqual(steps().map(event => event.delta), [3, -1]);
});
test('cancel and long press never become a tap', () => {
  const { engine, steps } = harness(); engine.down(50, 0); engine.cancel(); engine.up(50, 80); engine.down(50, 100); engine.up(50, 600); assert.deepEqual(steps(), []);
});
test('a swipe without intermediate move events is not a tap', () => {
  const { engine, steps } = harness(); engine.down(200, 0); engine.up(100, 100); assert.deepEqual(steps(), [{ delta: 3, tap: false }]);
});
test('invalid coordinates safely cancel', () => {
  const { engine, steps } = harness(); engine.down(100, 0); engine.move(NaN, 16); engine.up(100, 32); assert.deepEqual(steps(), []);
});
test('preview values stay in range and subscribers update immediately', () => {
  const volume = new PreviewVolume(), values = []; volume.subscribe(value => values.push(value)); volume.set(200); volume.step(-100); volume.set(NaN);
  assert.deepEqual(values, [10, 15, 0]);
});
