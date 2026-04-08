import { Injectable, inject } from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { map, shareReplay } from 'rxjs/operators';

export const MOBILE_BREAKPOINT = '(max-width: 768px)';

@Injectable({ providedIn: 'root' })
export class ResponsiveService {
  private bp = inject(BreakpointObserver);

  isMobile$ = this.bp.observe(MOBILE_BREAKPOINT).pipe(
    map((s) => s.matches),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
}
