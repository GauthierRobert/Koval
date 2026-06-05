export type TouchGestureState = 'undecided' | 'scrub' | 'pan' | 'scroll';

/**
 * Touch gesture detector for the chart stack.
 * - 'scrub' (finger drives the tooltip crosshair) is entered ONLY via long-press
 *   ({@link forceScrub}), never from movement alone.
 * - A horizontal drag resolves to 'pan' when the chart is zoomed, otherwise 'scroll'.
 * - A vertical drag resolves to 'scroll' (the page keeps scrolling).
 * Tiny movements stay 'undecided' so finger jitter doesn't cancel the long-press.
 */
export class TouchScrubGesture {
  private startX = 0;
  private startY = 0;
  private state: TouchGestureState = 'undecided';
  private canvas: HTMLCanvasElement | null = null;

  begin(canvas: HTMLCanvasElement, x: number, y: number): void {
    this.canvas = canvas;
    this.startX = x;
    this.startY = y;
    this.state = 'undecided';
  }

  end(): void {
    this.canvas = null;
    this.state = 'undecided';
  }

  get activeCanvas(): HTMLCanvasElement | null {
    return this.canvas;
  }

  /** Long-press fired while still undecided: lock into scrub mode. Returns whether it locked. */
  forceScrub(): boolean {
    if (this.state !== 'undecided' || !this.canvas) return false;
    this.state = 'scrub';
    return true;
  }

  /** Returns the resolved gesture after this move (still 'undecided' if below threshold). */
  classify(x: number, y: number, allowPan: boolean, threshold = 8): TouchGestureState {
    if (this.state !== 'undecided') return this.state;
    const dx = Math.abs(x - this.startX);
    const dy = Math.abs(y - this.startY);
    if (dx < threshold && dy < threshold) return 'undecided';
    this.state = dx >= dy && allowPan ? 'pan' : 'scroll';
    return this.state;
  }
}

/**
 * Sync the set of observed canvases on the resize observer. Canvases that disappear
 * from the input list are unobserved; new ones are observed and added to `tracked`.
 */
export function syncObservedCanvases(
  observer: ResizeObserver,
  tracked: Set<HTMLCanvasElement>,
  current: (HTMLCanvasElement | undefined)[],
): void {
  const filtered = current.filter((c): c is HTMLCanvasElement => !!c);
  const currentSet = new Set(filtered);
  for (const c of tracked) {
    if (!currentSet.has(c)) {
      observer.unobserve(c);
      tracked.delete(c);
    }
  }
  for (const c of filtered) {
    if (!tracked.has(c)) {
      observer.observe(c);
      tracked.add(c);
    }
  }
}

/** Register a touchmove listener on each canvas, outside the Angular zone. */
export function attachTouchMoveListeners(
  canvases: (HTMLCanvasElement | undefined)[],
  listener: (e: TouchEvent) => void,
): void {
  for (const c of canvases) {
    if (!c) continue;
    c.removeEventListener('touchmove', listener);
    c.addEventListener('touchmove', listener, {passive: false});
  }
}

export function detachTouchMoveListeners(
  canvases: (HTMLCanvasElement | undefined)[],
  listener: (e: TouchEvent) => void,
): void {
  for (const c of canvases) {
    c?.removeEventListener('touchmove', listener);
  }
}
