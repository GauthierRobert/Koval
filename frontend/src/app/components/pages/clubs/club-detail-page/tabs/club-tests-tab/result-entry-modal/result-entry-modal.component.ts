import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import {
  ClubTestIteration,
  ClubTestResult,
  RecordResultRequest,
  SegmentResultUnit,
  TestSegment,
} from '../../../../../../../services/club-test.service';
import { unitLabelKey } from '../test-display.utils';

interface SegmentEntry {
  segment: TestSegment;
  // Time-based units use mm/ss inputs; numeric units use plain number.
  minutes: number | null;
  seconds: number | null;
  numericValue: number | null;
}

@Component({
  selector: 'app-result-entry-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './result-entry-modal.component.html',
  styleUrl: './result-entry-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResultEntryModalComponent implements OnInit {
  @Input() iteration!: ClubTestIteration;
  @Input() existing: ClubTestResult | null = null;
  @Input() recordingForAthleteId: string | null = null;
  @Input() coachMode = false;
  @Output() cancelled = new EventEmitter<void>();
  @Output() save = new EventEmitter<RecordResultRequest>();

  entries: SegmentEntry[] = [];
  notes = '';
  athleteIdInput = '';

  ngOnInit(): void {
    this.athleteIdInput = this.recordingForAthleteId ?? '';
    this.entries = this.iteration.segments.map((s) => {
      const v = this.existing?.segmentResults?.[s.id]?.value;
      return this.toEntry(s, v);
    });
    if (this.existing?.notes) this.notes = this.existing.notes;
  }

  isTimeUnit(u: SegmentResultUnit): boolean {
    return u === 'SECONDS' || u === 'PACE_S_PER_KM' || u === 'PACE_S_PER_100M';
  }

  unitLabelKey(u: SegmentResultUnit | string): string {
    return unitLabelKey(u);
  }

  trackBySegId(_idx: number, e: SegmentEntry): string {
    return e.segment.id;
  }

  submit(): void {
    const segmentResults: RecordResultRequest['segmentResults'] = {};
    for (const e of this.entries) {
      const value = this.entryToValue(e);
      if (value === null || isNaN(value)) continue;
      segmentResults[e.segment.id] = { value, unit: e.segment.resultUnit };
    }
    if (Object.keys(segmentResults).length === 0) return;
    const req: RecordResultRequest = {
      athleteId: this.coachMode && this.athleteIdInput ? this.athleteIdInput : null,
      segmentResults,
      notes: this.notes || null,
    };
    this.save.emit(req);
  }

  private toEntry(seg: TestSegment, existingValue: number | undefined): SegmentEntry {
    if (this.isTimeUnit(seg.resultUnit) && existingValue !== undefined) {
      const total = Math.round(existingValue);
      return {
        segment: seg,
        minutes: Math.floor(total / 60),
        seconds: total % 60,
        numericValue: null,
      };
    }
    return {
      segment: seg,
      minutes: null,
      seconds: null,
      numericValue: existingValue ?? null,
    };
  }

  private entryToValue(e: SegmentEntry): number | null {
    if (this.isTimeUnit(e.segment.resultUnit)) {
      const min = e.minutes ?? 0;
      const sec = e.seconds ?? 0;
      const total = Number(min) * 60 + Number(sec);
      return total > 0 ? total : null;
    }
    return e.numericValue !== null ? Number(e.numericValue) : null;
  }
}
