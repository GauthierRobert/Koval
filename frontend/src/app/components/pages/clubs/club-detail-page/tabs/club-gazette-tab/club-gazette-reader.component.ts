import {AsyncPipe, DatePipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, Input, OnChanges} from '@angular/core';
import {Observable} from 'rxjs';
import {shareReplay, tap} from 'rxjs/operators';
import {ClubGazetteService} from '../../../../../../services/club-gazette.service';
import {ClubGazetteEditionResponse} from '../../../../../../models/club-gazette.model';
import {KovalImageComponent} from '../../../../../shared/koval-image/koval-image.component';

@Component({
  selector: 'club-gazette-reader',
  standalone: true,
  imports: [AsyncPipe, DatePipe, KovalImageComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './club-gazette-reader.component.html',
  styleUrls: ['./club-gazette-reader.component.css'],
})
export class ClubGazetteReaderComponent implements OnChanges {
  @Input() clubId!: string;
  @Input() editionId!: string;

  edition$!: Observable<ClubGazetteEditionResponse>;
  pdfUrl = '';

  private gazetteService = inject(ClubGazetteService);
  readonly postsState$ = this.gazetteService.posts$;

  ngOnChanges(): void {
    if (!this.clubId || !this.editionId) return;
    this.edition$ = this.gazetteService.getEdition(this.clubId, this.editionId).pipe(
      tap(e => {
        if (e.status === 'PUBLISHED') {
          this.gazetteService.markAsRead(this.clubId, this.editionId).subscribe({error: () => {}});
        }
      }),
      shareReplay({bufferSize: 1, refCount: true}),
    );
    this.gazetteService.loadPosts(this.clubId, this.editionId);
    this.pdfUrl = this.gazetteService.pdfUrl(this.clubId, this.editionId);
  }
}
