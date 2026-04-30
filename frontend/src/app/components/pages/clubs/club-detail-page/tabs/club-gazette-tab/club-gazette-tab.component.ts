import {ChangeDetectionStrategy, Component, DestroyRef, inject, Input, OnChanges, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AsyncPipe, DatePipe} from '@angular/common';
import {ActivatedRoute, Router} from '@angular/router';
import {ClubGazetteService} from '../../../../../../services/club-gazette.service';
import {
  ClubGazetteEditionResponse,
  ClubGazetteEditionSummary,
} from '../../../../../../models/club-gazette.model';
import {ClubGazetteReaderComponent} from './club-gazette-reader.component';
import {ClubGazetteComposerComponent} from './club-gazette-composer.component';

type View = 'list' | 'reader' | 'composer';

/**
 * Top-level tab for the Club Gazette feature. Three views:
 *   list     — published editions grid + a hero CTA for the current draft
 *   reader   — a single edition (published or draft preview)
 *   composer — create or edit a member post in the current draft
 *
 * View state is encoded in the URL via query params (`?view=reader&editionId=…`,
 * `?view=composer`) so the browser back button moves between gazette views.
 */
@Component({
  selector: 'app-club-gazette-tab',
  standalone: true,
  imports: [AsyncPipe, DatePipe, ClubGazetteReaderComponent, ClubGazetteComposerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './club-gazette-tab.component.html',
  styleUrls: ['./club-gazette-tab.component.css'],
})
export class ClubGazetteTabComponent implements OnInit, OnChanges {
  @Input() clubId!: string;

  readonly view = signal<View>('list');
  readonly selectedEditionId = signal<string | null>(null);
  readonly currentDraft = signal<ClubGazetteEditionResponse | null>(null);

  private gazetteService = inject(ClubGazetteService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  readonly editions$ = this.gazetteService.editions$;

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const v = params.get('view');
        const editionId = params.get('editionId');
        if (v === 'reader' && editionId) {
          this.selectedEditionId.set(editionId);
          this.view.set('reader');
        } else if (v === 'composer') {
          this.selectedEditionId.set(null);
          this.view.set('composer');
        } else {
          this.selectedEditionId.set(null);
          this.view.set('list');
        }
      });
  }

  ngOnChanges(): void {
    if (this.clubId) this.refresh();
  }

  private refresh(): void {
    this.gazetteService.loadEditions(this.clubId);
    this.gazetteService.getCurrentDraft(this.clubId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: d => this.currentDraft.set(d),
        error: () => this.currentDraft.set(null),
      });
  }

  openEdition(e: ClubGazetteEditionSummary | ClubGazetteEditionResponse): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { view: 'reader', editionId: e.id },
      queryParamsHandling: 'merge',
    });
  }

  openDraftPreview(): void {
    const d = this.currentDraft();
    if (!d) return;
    this.openEdition(d);
  }

  openComposer(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { view: 'composer', editionId: null },
      queryParamsHandling: 'merge',
    });
  }

  back(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { view: null, editionId: null },
      queryParamsHandling: 'merge',
    });
    this.refresh();
  }

  onPostSaved(): void {
    this.back();
  }
}
