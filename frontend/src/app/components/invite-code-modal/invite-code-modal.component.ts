import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest, map, Observable } from 'rxjs';
import { CoachService, InviteCode } from '../../services/coach.service';
import { AuthService } from '../../services/auth.service';
import { Tag, TagService } from '../../services/tag.service';

@Component({
  selector: 'app-invite-code-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="modal-backdrop" *ngIf="isOpen" (click)="close()">
      <div class="modal-card glass" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h2>INVITE <span class="highlight">CODES</span></h2>
          <button class="close-btn" (click)="close()">&times;</button>
        </div>

        <div class="modal-body">
          <!-- Generate new code -->
          <div class="generate-section">
            <h3 class="sub-title">GENERATE NEW CODE</h3>

            <div class="field">
              <label>Tags (athletes will receive these)</label>
              <div class="tag-selector">
                <span
                  *ngFor="let tag of unassignedTags"
                  class="tag-chip"
                  [class.selected]="selectedTagIds.includes(tag.id)"
                  (click)="toggleTag(tag)"
                >{{ tag.name }}</span>
                <div class="tag-add-inline" *ngIf="unassignedTags.length === 0 || showNewTagInput">
                  <input
                    class="tag-input"
                    [(ngModel)]="newTagInput"
                    (keydown.enter)="addNewTag()"
                    placeholder="New tag..."
                  />
                  <button class="tag-add-btn" (click)="addNewTag()">+</button>
                </div>
                <button
                  class="tag-new-btn"
                  *ngIf="!showNewTagInput && unassignedTags.length > 0"
                  (click)="showNewTagInput = true"
                >+ New tag</button>
              </div>
            </div>

            <div class="field">
              <label>Max uses (0 = unlimited)</label>
              <input type="number" class="num-input" [(ngModel)]="maxUses" min="0" />
            </div>

            <button class="generate-btn" (click)="generate()" [disabled]="generating">
              {{ generating ? 'GENERATING...' : 'GENERATE CODE' }}
            </button>
          </div>

          <!-- Generated code display -->
          <div class="code-display" *ngIf="generatedCode">
            <div class="code-display-inner">
              <span class="code-display-label">Share this code with your athletes</span>
              <div class="code-value">{{ generatedCode }}</div>
            </div>
            <button class="copy-btn" (click)="copyCode()">
              {{ copied ? 'COPIED!' : 'COPY' }}
            </button>
          </div>

          <!-- Existing codes -->
          <div class="codes-section" *ngIf="(inviteCodes$ | async)?.length">
            <div class="codes-header">
              <h3 class="sub-title">EXISTING CODES</h3>
              <button class="toggle-inactive-btn" (click)="toggleShowInactive()" *ngIf="hasInactiveCodes$ | async">
                {{ showInactive ? 'HIDE' : 'SHOW' }} INACTIVE
              </button>
            </div>
            <div class="code-list">
              <div *ngFor="let code of displayedCodes$ | async" class="code-row" [class.inactive]="!code.active">
                <div class="code-left">
                  <span class="code-mono">{{ code.code }}</span>
                  <span class="code-status-dot" [class.active-dot]="code.active"></span>
                </div>
                <div class="code-meta">
                  <div class="code-tags">
                    <span *ngFor="let tagId of code.tags" class="mini-tag">{{ resolveTagName(tagId) }}</span>
                    <span *ngIf="code.tags.length === 0" class="no-tags">No tags</span>
                  </div>
                  <span class="code-uses">{{ code.currentUses }}{{ code.maxUses > 0 ? ' / ' + code.maxUses : '' }} uses</span>
                </div>
                <button
                  class="deactivate-btn"
                  *ngIf="code.active"
                  (click)="deactivate(code)"
                  title="Deactivate"
                >&times;</button>
                <span class="inactive-badge" *ngIf="!code.active">INACTIVE</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.55);
      backdrop-filter: blur(6px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal-card {
      width: 560px;
      max-height: 85vh;
      border-radius: 20px;
      padding: 2rem 2.25rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      overflow-y: auto;
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 0.75rem;
      border-bottom: 1px solid var(--glass-border);
    }

    .modal-header h2 {
      font-size: 16px;
      font-weight: 800;
      letter-spacing: 3px;
    }

    .highlight { color: var(--accent-color); }

    .close-btn {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.1);
      color: var(--text-muted);
      font-size: 18px;
      width: 32px;
      height: 32px;
      border-radius: 8px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }

    .close-btn:hover { background: rgba(255, 255, 255, 0.1); color: var(--text-color); }

    .modal-body {
      display: flex;
      flex-direction: column;
      gap: 2rem;
    }

    .generate-section {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    .sub-title {
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 1.5px;
      color: var(--text-muted);
      margin: 0;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .field label {
      font-size: 11px;
      font-weight: 700;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .tag-selector {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      align-items: center;
      min-height: 36px;
    }

    .tag-chip {
      font-size: 12px;
      font-weight: 700;
      padding: 6px 14px;
      border-radius: 20px;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.12);
      color: #bbb;
      cursor: pointer;
      transition: all 0.2s;
    }

    .tag-chip:hover {
      background: rgba(255, 157, 0, 0.1);
      border-color: rgba(255, 157, 0, 0.25);
      color: var(--accent-color);
    }

    .tag-chip.selected {
      background: rgba(255, 157, 0, 0.18);
      border-color: var(--accent-color);
      color: var(--accent-color);
    }

    .tag-add-inline { display: flex; align-items: center; gap: 6px; }

    .tag-input {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 20px;
      padding: 6px 14px;
      color: #e0e0e0;
      font-size: 12px;
      font-family: inherit;
      width: 120px;
      outline: none;
      transition: border-color 0.2s;
    }

    .tag-input:focus { border-color: var(--accent-color); }

    .tag-add-btn {
      background: rgba(255, 157, 0, 0.15);
      border: 1px solid rgba(255, 157, 0, 0.3);
      color: var(--accent-color);
      width: 28px;
      height: 28px;
      border-radius: 50%;
      font-size: 14px;
      font-weight: 800;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0;
      transition: all 0.2s;
    }

    .tag-add-btn:hover { background: rgba(255, 157, 0, 0.25); }

    .tag-new-btn {
      font-size: 11px;
      font-weight: 700;
      padding: 6px 12px;
      border-radius: 20px;
      background: none;
      border: 1px dashed rgba(255, 255, 255, 0.18);
      color: var(--text-muted);
      cursor: pointer;
      transition: all 0.2s;
    }

    .tag-new-btn:hover { color: var(--accent-color); border-color: var(--accent-color); }

    .num-input {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 10px;
      padding: 10px 14px;
      color: #e0e0e0;
      font-size: 14px;
      font-family: inherit;
      width: 110px;
      outline: none;
      transition: border-color 0.2s;
    }

    .num-input:focus { border-color: var(--accent-color); }

    .generate-btn {
      background: var(--accent-color);
      color: #000;
      border: none;
      padding: 12px 28px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 800;
      letter-spacing: 0.5px;
      cursor: pointer;
      transition: filter 0.2s;
      align-self: flex-start;
    }

    .generate-btn:hover { filter: brightness(1.15); }
    .generate-btn:disabled { opacity: 0.5; cursor: not-allowed; }

    .code-display {
      background: rgba(255, 157, 0, 0.06);
      border: 1px solid rgba(255, 157, 0, 0.25);
      border-radius: 16px;
      padding: 1.5rem 1.75rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
    }

    .code-display-inner {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .code-display-label {
      font-size: 10px;
      font-weight: 700;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .code-value {
      font-size: 34px;
      font-weight: 800;
      font-family: 'SF Mono', 'Fira Code', monospace;
      letter-spacing: 6px;
      color: var(--accent-color);
    }

    .copy-btn {
      background: rgba(255, 157, 0, 0.12);
      border: 1px solid rgba(255, 157, 0, 0.3);
      color: var(--accent-color);
      padding: 10px 20px;
      border-radius: 10px;
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.5px;
      cursor: pointer;
      white-space: nowrap;
      transition: all 0.2s;
    }

    .copy-btn:hover { background: rgba(255, 157, 0, 0.2); }

    .codes-section {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .codes-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .toggle-inactive-btn {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      color: var(--text-muted);
      font-size: 9px;
      font-weight: 700;
      letter-spacing: 0.5px;
      padding: 4px 10px;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .toggle-inactive-btn:hover {
      background: rgba(255, 255, 255, 0.1);
      color: var(--text-color);
    }

    .code-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .code-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 16px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid rgba(255, 255, 255, 0.06);
      gap: 16px;
      transition: background 0.2s;
    }

    .code-row:hover { background: rgba(255, 255, 255, 0.05); }
    .code-row.inactive { opacity: 0.35; }

    .code-left {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;
    }

    .code-status-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--text-muted);
    }

    .code-status-dot.active-dot { background: var(--success-color); }

    .code-mono {
      font-family: 'SF Mono', 'Fira Code', monospace;
      font-size: 14px;
      font-weight: 700;
      letter-spacing: 2px;
      color: var(--text-color);
    }

    .code-meta {
      display: flex;
      align-items: center;
      gap: 12px;
      flex: 1;
      justify-content: flex-end;
    }

    .code-tags { display: flex; gap: 4px; flex-wrap: wrap; }

    .mini-tag {
      font-size: 9px;
      font-weight: 800;
      padding: 3px 8px;
      border-radius: 6px;
      background: rgba(255, 157, 0, 0.12);
      color: var(--accent-color);
    }

    .no-tags {
      font-size: 10px;
      color: var(--text-muted);
      font-style: italic;
    }

    .code-uses {
      font-size: 11px;
      color: var(--text-muted);
      white-space: nowrap;
    }

    .deactivate-btn {
      background: rgba(248, 113, 113, 0.1);
      border: 1px solid rgba(248, 113, 113, 0.25);
      color: var(--danger-color);
      width: 28px;
      height: 28px;
      border-radius: 8px;
      font-size: 16px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0;
      flex-shrink: 0;
      transition: all 0.2s;
    }

    .deactivate-btn:hover { background: rgba(248, 113, 113, 0.2); }

    .inactive-badge {
      font-size: 9px;
      font-weight: 800;
      color: var(--text-muted);
      letter-spacing: 1px;
      padding: 4px 10px;
      border-radius: 6px;
      background: rgba(255, 255, 255, 0.05);
      flex-shrink: 0;
    }

    ::-webkit-scrollbar { width: 4px; }
    ::-webkit-scrollbar-track { background: transparent; }
    ::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.06); border-radius: 2px; }
  `]
})
export class InviteCodeModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() availableTags: Tag[] = [];
  @Output() closed = new EventEmitter<void>();
  @Output() codeGenerated = new EventEmitter<InviteCode>();

  selectedTagIds: string[] = [];
  maxUses = 0;
  generating = false;
  generatedCode: string | null = null;
  copied = false;
  newTagInput = '';
  showNewTagInput = false;
  showInactive = false;
  unassignedTags: Tag[] = [];

  private userId = '';
  private inviteCodesSubject = new BehaviorSubject<InviteCode[]>([]);
  inviteCodes$ = this.inviteCodesSubject.asObservable();

  private showInactiveSubject = new BehaviorSubject<boolean>(false);

  hasInactiveCodes$: Observable<boolean> = this.inviteCodes$.pipe(
    map(codes => codes.some(c => !c.active))
  );

  displayedCodes$: Observable<InviteCode[]> = combineLatest([
    this.inviteCodes$,
    this.showInactiveSubject,
  ]).pipe(
    map(([codes, show]) => show ? codes : codes.filter(c => c.active))
  );

  constructor(
    private coachService: CoachService,
    private authService: AuthService,
    private tagService: TagService
  ) {
    this.authService.user$.subscribe(u => {
      if (u) this.userId = u.id;
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.generatedCode = null;
      this.copied = false;
      this.selectedTagIds = [];
      this.maxUses = 0;
      this.showNewTagInput = false;
      this.newTagInput = '';
      this.loadCodes();
    }
  }

  loadCodes(): void {
    this.coachService.getInviteCodes().subscribe({
      next: (codes) => {
        this.inviteCodesSubject.next(codes);
        this.computeUnassignedTags(codes);
      },
    });
  }

  private computeUnassignedTags(codes: InviteCode[]): void {
    const usedTagIds = new Set<string>();
    for (const code of codes) {
      if (code.active) {
        code.tags.forEach(t => usedTagIds.add(t));
      }
    }
    this.unassignedTags = this.availableTags.filter(t => !usedTagIds.has(t.id));
  }

  toggleShowInactive(): void {
    this.showInactive = !this.showInactive;
    this.showInactiveSubject.next(this.showInactive);
  }

  toggleTag(tag: Tag): void {
    const idx = this.selectedTagIds.indexOf(tag.id);
    if (idx >= 0) {
      this.selectedTagIds.splice(idx, 1);
    } else {
      this.selectedTagIds.push(tag.id);
    }
  }

  addNewTag(): void {
    const name = this.newTagInput.trim();
    if (!name) return;
    // Create tag on backend first, then add to local state
    this.tagService.createTag(name).subscribe({
      next: (tag) => {
        if (!this.availableTags.find(t => t.id === tag.id)) {
          this.availableTags = [...this.availableTags, tag];
        }
        if (!this.unassignedTags.find(t => t.id === tag.id)) {
          this.unassignedTags = [...this.unassignedTags, tag];
        }
        if (!this.selectedTagIds.includes(tag.id)) {
          this.selectedTagIds.push(tag.id);
        }
        this.newTagInput = '';
        this.showNewTagInput = false;
      },
    });
  }

  resolveTagName(tagId: string): string {
    const tag = this.availableTags.find(t => t.id === tagId);
    return tag ? tag.name : tagId;
  }

  generate(): void {
    this.generating = true;
    this.coachService.generateInviteCode(this.selectedTagIds, this.maxUses).subscribe({
      next: (code) => {
        this.generatedCode = code.code;
        this.generating = false;
        this.codeGenerated.emit(code);
        this.loadCodes();
      },
      error: () => {
        this.generating = false;
      },
    });
  }

  copyCode(): void {
    if (this.generatedCode) {
      navigator.clipboard.writeText(this.generatedCode);
      this.copied = true;
      setTimeout(() => (this.copied = false), 2000);
    }
  }

  deactivate(code: InviteCode): void {
    this.coachService.deactivateInviteCode(code.id).subscribe({
      next: () => this.loadCodes(),
    });
  }

  close(): void {
    this.closed.emit();
  }
}
