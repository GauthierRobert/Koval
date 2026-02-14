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
  templateUrl: './invite-code-modal.component.html',
  styleUrl: './invite-code-modal.component.css',
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
