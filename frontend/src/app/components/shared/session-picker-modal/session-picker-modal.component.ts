import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, combineLatest, Observable, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { ModalShellComponent } from '../modal-shell/modal-shell.component';
import { SportIconComponent } from '../sport-icon/sport-icon.component';
import {
  SessionComparisonService,
  SimilarSessionDto,
} from '../../../services/session-comparison.service';
import { SavedSession } from '../../../services/history.service';
import { formatTimeText } from '../format/format.utils';

interface PickerView {
  similar: SimilarSessionDto[];
  others: SimilarSessionDto[];
  loading: boolean;
}

/**
 * Picks 1-3 same-sport sessions to compare against an anchor. Sport is locked to the anchor.
 * Top section: similarity-ranked candidates from `/similar`. Bottom section: the rest of the
 * user's same-sport sessions surfaced via the same endpoint with a larger limit.
 */
@Component({
  selector: 'app-session-picker-modal',
  standalone: true,
  imports: [CommonModule, TranslateModule, ModalShellComponent, SportIconComponent],
  templateUrl: './session-picker-modal.component.html',
  styleUrl: './session-picker-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionPickerModalComponent implements OnChanges {
  private comparisonService = inject(SessionComparisonService);

  @Input() open = false;
  @Input() anchor: SavedSession | null = null;

  /** Max additional sessions selectable on top of the anchor (4 columns total). */
  @Input() maxSelectable = 3;

  @Output() closed = new EventEmitter<void>();
  @Output() apply = new EventEmitter<string[]>();

  private trigger$ = new BehaviorSubject<string | null>(null);
  selected = new Set<string>();

  view$: Observable<PickerView> = this.trigger$.pipe(
    switchMap((seedId) => {
      if (!seedId) return of<PickerView>({ similar: [], others: [], loading: false });
      // Fetch a large window so we can split into similar (top) + others below.
      return combineLatest([
        this.comparisonService.findSimilar(seedId, 10),
        this.comparisonService.findSimilar(seedId, 200),
      ]).pipe(
        map(([top, all]) => {
          const topIds = new Set(top.map((s) => s.id));
          const others = all.filter((s) => !topIds.has(s.id));
          return { similar: top, others, loading: false };
        }),
        catchError(() => of<PickerView>({ similar: [], others: [], loading: false })),
      );
    }),
  );

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open && this.anchor) {
      this.selected = new Set<string>();
      this.trigger$.next(this.anchor.id);
    } else if (changes['open'] && !this.open) {
      this.trigger$.next(null);
    }
  }

  toggle(id: string): void {
    if (this.selected.has(id)) {
      this.selected.delete(id);
      return;
    }
    if (this.selected.size >= this.maxSelectable) return;
    this.selected.add(id);
  }

  isAtCap(id: string): boolean {
    return !this.selected.has(id) && this.selected.size >= this.maxSelectable;
  }

  onSubmit(): void {
    if (!this.anchor || this.selected.size === 0) return;
    this.apply.emit([this.anchor.id, ...Array.from(this.selected)]);
  }

  onClose(): void {
    this.closed.emit();
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString(undefined, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  }

  formatDuration(seconds: number): string {
    return formatTimeText(seconds);
  }
}
