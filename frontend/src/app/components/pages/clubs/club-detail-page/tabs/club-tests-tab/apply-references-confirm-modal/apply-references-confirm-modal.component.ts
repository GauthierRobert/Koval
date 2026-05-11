import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import {
  ClubTestIteration,
  ClubTestResult,
  ReferenceUpdateRule,
} from '../../../../../../../services/club-test.service';
import { targetLabelKey } from '../test-display.utils';

@Component({
  selector: 'app-apply-references-confirm-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './apply-references-confirm-modal.component.html',
  styleUrl: './apply-references-confirm-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplyReferencesConfirmModalComponent implements OnInit {
  @Input() iteration!: ClubTestIteration;
  @Input() result!: ClubTestResult;
  @Output() cancel = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<string[]>();

  selected = new Set<string>();

  ngOnInit(): void {
    // Default-select every rule that has a successfully computed reference.
    for (const r of this.iteration.referenceUpdates) {
      if (this.result.computedReferences[r.id] !== undefined) {
        this.selected.add(r.id);
      }
    }
  }

  isSelected(ruleId: string): boolean {
    return this.selected.has(ruleId);
  }

  toggle(ruleId: string): void {
    if (this.selected.has(ruleId)) this.selected.delete(ruleId);
    else this.selected.add(ruleId);
  }

  computedFor(ruleId: string): number | undefined {
    return this.result.computedReferences[ruleId];
  }

  rules(): ReferenceUpdateRule[] {
    return this.iteration.referenceUpdates;
  }

  targetLabelKey(t: string): string {
    return targetLabelKey(t);
  }

  apply(): void {
    if (this.selected.size === 0) return;
    this.confirm.emit(Array.from(this.selected));
  }
}
