/**
 * Touch scrub gesture detector. Decides scrub-vs-scroll from the initial drag direction
 * and ignores tiny movements until a clear direction emerges.
 */
export class TouchScrubGesture {
  private startX = 0;
  private startY = 0;
  private state: 'undecided' | 'scrub' | 'scroll' = 'undecided';
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

  isScrubbing(): boolean {
    return this.state === 'scrub';
  }

  isScrolling(): boolean {
    return this.state === 'scroll';
  }

  /** Returns the resolved gesture after this move (still 'undecided' if below threshold). */
  classify(x: number, y: number, threshold = 4): 'undecided' | 'scrub' | 'scroll' {
    if (this.state !== 'undecided') return this.state;
    const dx = Math.abs(x - this.startX);
    const dy = Math.abs(y - this.startY);
    if (dx < threshold && dy < threshold) return 'undecided';
    this.state = dx >= dy ? 'scrub' : 'scroll';
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
