import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule} from '@ngx-translate/core';
import {BehaviorSubject, combineLatest, map, Observable} from 'rxjs';
import {CoachService, InviteCode} from '../../../services/coach.service';
import {Group, GroupService} from '../../../services/group.service';
import {ModalShellComponent} from '../modal-shell/modal-shell.component';

@Component({
  selector: 'app-invite-code-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, ModalShellComponent],
  templateUrl: './invite-code-modal.component.html',
  styleUrl: './invite-code-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InviteCodeModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() availableTags: Group[] = [];
  @Output() closed = new EventEmitter<void>();
  @Output() codeGenerated = new EventEmitter<InviteCode>();

  selectedTagId: string | null = null;
  maxUses = 0;
  generating = false;
  generatedCode: string | null = null;
  copied = false;
  newTagInput = '';
  showInactive = false;
  unassignedTags: Group[] = [];

  private coachService = inject(CoachService);
  private groupService = inject(GroupService);

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

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.generatedCode = null;
      this.copied = false;
      this.selectedTagId = null;
      this.maxUses = 0;
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
    const usedGroupIds = new Set<string>();
    for (const code of codes) {
      if (code.active) {
        code.groupIds.forEach(g => usedGroupIds.add(g));
      }
    }
    this.unassignedTags = this.availableTags.filter(t => !usedGroupIds.has(t.id));
  }

  toggleShowInactive(): void {
    this.showInactive = !this.showInactive;
    this.showInactiveSubject.next(this.showInactive);
  }

  selectTag(group: Group): void {
    this.selectedTagId = this.selectedTagId === group.id ? null : group.id;
  }

  addNewTag(): void {
    const name = this.newTagInput.trim();
    if (!name) return;
    this.groupService.createGroup(name).subscribe({
      next: (tag) => {
        if (!this.availableTags.find(t => t.id === tag.id)) {
          this.availableTags = [...this.availableTags, tag];
        }
        this.newTagInput = '';
        // Auto-generate invite code using tag name as the code
        this.generating = true;
        this.coachService.generateInviteCode([tag.id], this.maxUses, name.toUpperCase()).subscribe({
          next: (inviteCode) => {
            this.generatedCode = inviteCode.code;
            this.generating = false;
            this.codeGenerated.emit(inviteCode);
            this.loadCodes();
          },
          error: () => { this.generating = false; },
        });
      },
    });
  }

  resolveTagName(groupId: string): string {
    const group = this.availableTags.find(g => g.id === groupId);
    return group ? group.name : groupId;
  }

  generate(): void {
    this.generating = true;
    this.coachService.generateInviteCode(this.selectedTagId ? [this.selectedTagId] : [], this.maxUses).subscribe({
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
