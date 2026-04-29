import {ChangeDetectionStrategy, Component, DestroyRef, inject, Input, OnChanges, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AsyncPipe, DatePipe} from '@angular/common';
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
 */
@Component({
  selector: 'app-club-gazette-tab',
  standalone: true,
  imports: [AsyncPipe, DatePipe, ClubGazetteReaderComponent, ClubGazetteComposerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './club-gazette-tab.component.html',
  styleUrls: ['./club-gazette-tab.component.css'],
})
export class ClubGazetteTabComponent implements OnChanges {
  @Input() clubId!: string;

  readonly view = signal<View>('list');
  readonly selectedEditionId = signal<string | null>(null);
  readonly currentDraft = signal<ClubGazetteEditionResponse | null>(null);

  private gazetteService = inject(ClubGazetteService);
  private destroyRef = inject(DestroyRef);
  readonly editions$ = this.gazetteService.editions$;

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
    this.selectedEditionId.set(e.id);
    this.view.set('reader');
  }

  openDraftPreview(): void {
    const d = this.currentDraft();
    if (!d) return;
    this.openEdition(d);
  }

  openComposer(): void {
    this.view.set('composer');
  }

  back(): void {
    this.view.set('list');
    this.refresh();
  }

  onPostSaved(): void {
    this.back();
  }
}
