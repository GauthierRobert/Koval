import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, Input, OnInit, output, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {firstValueFrom} from 'rxjs';
import {ClubGazetteService} from '../../../../../../services/club-gazette.service';
import {AuthService} from '../../../../../../services/auth.service';
import {
  ClubGazetteEditionResponse,
  ClubGazettePostResponse,
  CreateGazettePostRequest,
  GazettePostType,
} from '../../../../../../models/club-gazette.model';
import {KovalPhotoUploaderComponent} from '../../../../../shared/koval-photo-uploader/koval-photo-uploader.component';
import {GazetteComposerAsideLeftComponent} from './composer-aside/gazette-composer-aside-left.component';
import {GazetteComposerAsideRightComponent} from './composer-aside/gazette-composer-aside-right.component';

interface PostTypeTile {
  value: GazettePostType;
  label: string;
  hint: string;
  icon: string;
}

const POST_TYPES: PostTypeTile[] = [
  {value: 'REFLECTION', label: 'Reflection', hint: 'Free thought · week summary', icon: 'spark'},
  {value: 'SESSION_RECAP', label: 'Session recap', hint: 'Debrief a club session', icon: 'run'},
  {value: 'RACE_RESULT', label: 'Race result', hint: 'Share a race finish', icon: 'flag'},
  {value: 'PERSONAL_WIN', label: 'Personal win', hint: 'PR · milestone · achievement', icon: 'trophy'},
  {value: 'SHOUTOUT', label: 'Shoutout', hint: 'Recognise a teammate', icon: 'heart'},
];

const TITLE_MAX = 100;
const CONTENT_MAX = 2000;

/**
 * Three-column composer for a member contribution to the current draft.
 *
 * Left rail: target edition card + tips + my-contributions.
 * Center: type tiles + title + content + photo dropzone + linked session.
 * Right rail: live preview + summary stats + recent posts.
 *
 * Linked-session input remains a raw ID for now — wiring an autocomplete
 * needs the existing club session services. Marked as TODO inline.
 */
@Component({
  selector: 'club-gazette-composer',
  standalone: true,
  imports: [
    FormsModule,
    KovalPhotoUploaderComponent,
    GazetteComposerAsideLeftComponent,
    GazetteComposerAsideRightComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './club-gazette-composer.component.html',
  styleUrls: ['./club-gazette-composer.component.css'],
})
export class ClubGazetteComposerComponent implements OnInit {
  @Input() clubId!: string;
  readonly saved = output<void>();
  readonly cancelled = output<void>();

  readonly types = POST_TYPES;
  readonly titleMax = TITLE_MAX;
  readonly contentMax = CONTENT_MAX;
  readonly maxPhotos = 4;

  readonly type = signal<GazettePostType>('REFLECTION');
  readonly title = signal('');
  readonly content = signal('');
  readonly linkedSessionId = signal('');
  readonly linkedRaceGoalId = signal('');
  readonly mediaIds = signal<string[]>([]);
  readonly attachOpen = signal(false);

  readonly draft = signal<ClubGazetteEditionResponse | null>(null);
  readonly draftPostsCount = signal<number>(0);
  readonly draftPhotosCount = signal<number>(0);
  readonly myDraftPosts = signal<ClubGazettePostResponse[]>([]);
  readonly recentPosts = signal<ClubGazettePostResponse[]>([]);

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly authorName = signal<string>('');

  readonly photoCount = computed(() => this.mediaIds().length);
  readonly canSubmit = computed(
    () => !this.submitting() && this.content().trim().length > 0 && this.draft() !== null
  );
  readonly currentTile = computed(() => POST_TYPES.find(t => t.value === this.type())!);
  readonly titleCount = computed(() => this.title().length);
  readonly contentCount = computed(() => this.content().length);

  private gazetteService = inject(ClubGazetteService);
  private authService = inject(AuthService);
  private destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.gazetteService.getCurrentDraft(this.clubId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: d => {
          this.draft.set(d);
          this.loadPosts(d.id);
        },
        error: e => this.error.set(extractError(e)),
      });

    this.authService.user$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(u => {
        if (u) this.authorName.set(u.displayName || 'You');
      });
  }

  private loadPosts(editionId: string): void {
    this.gazetteService.loadPosts(this.clubId, editionId);
    this.gazetteService.posts$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => {
        if (!res) return;
        this.recentPosts.set(res.posts);
        this.draftPostsCount.set(res.posts.length);
        this.draftPhotosCount.set(res.posts.reduce((a, p) => a + p.photos.length, 0));
      });

    this.gazetteService.myCurrentDraftPosts(this.clubId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: posts => this.myDraftPosts.set(posts),
        error: () => {},
      });
  }

  selectType(t: GazettePostType): void {
    this.type.set(t);
    if (t !== 'SESSION_RECAP') this.linkedSessionId.set('');
    if (t !== 'RACE_RESULT') this.linkedRaceGoalId.set('');
  }

  toggleAttach(): void {
    this.attachOpen.update(v => !v);
  }

  onMediaUploaded(mediaId: string): void {
    this.mediaIds.update(arr => [...arr, mediaId]);
  }

  removePhoto(id: string): void {
    this.mediaIds.update(arr => arr.filter(x => x !== id));
  }

  cancel(): void {
    this.cancelled.emit();
  }

  async submit(): Promise<void> {
    const draft = this.draft();
    if (!draft || !this.content().trim()) return;
    this.submitting.set(true);
    this.error.set(null);

    const req: CreateGazettePostRequest = {
      type: this.type(),
      title: this.title().trim() || null,
      content: this.content().trim(),
      linkedSessionId: this.type() === 'SESSION_RECAP' ? this.linkedSessionId().trim() || null : null,
      linkedRaceGoalId: this.type() === 'RACE_RESULT' ? this.linkedRaceGoalId().trim() || null : null,
      mediaIds: this.mediaIds(),
    };

    try {
      await firstValueFrom(this.gazetteService.createPost(this.clubId, draft.id, req));
      this.saved.emit();
    } catch (e) {
      this.error.set(extractError(e));
    } finally {
      this.submitting.set(false);
    }
  }
}

function extractError(e: unknown): string {
  if (e && typeof e === 'object' && 'error' in e) {
    const err = (e as {error: unknown}).error;
    if (typeof err === 'string') return err;
    if (err && typeof err === 'object' && 'message' in err) return String((err as {message: unknown}).message);
  }
  if (e instanceof Error) return e.message;
  return 'Unknown error';
}
