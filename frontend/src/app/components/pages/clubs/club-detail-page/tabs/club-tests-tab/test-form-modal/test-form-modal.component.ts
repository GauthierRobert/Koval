import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ClubDetail } from '../../../../../../../models/club.model';
import {
  ClubTestDetail,
  ClubTestService,
  CreateClubTestRequest,
  RankingDirection,
  RankingMetric,
  ReferenceTarget,
  ReferenceUpdateRule,
  SegmentResultUnit,
  SportType,
  TestPreset,
  TestSegment,
} from '../../../../../../../services/club-test.service';
import { metricLabelKey, sportLabelKey, targetLabelKey, unitLabelKey } from '../test-display.utils';

const SPORTS: SportType[] = ['CYCLING', 'RUNNING', 'SWIMMING', 'BRICK'];
const UNITS: SegmentResultUnit[] = ['SECONDS', 'WATTS', 'PACE_S_PER_KM', 'PACE_S_PER_100M', 'METERS'];
const TARGETS: ReferenceTarget[] = [
  'FTP',
  'CRITICAL_SWIM_SPEED',
  'FUNCTIONAL_THRESHOLD_PACE',
  'PACE_5K',
  'PACE_10K',
  'PACE_HALF_MARATHON',
  'PACE_MARATHON',
  'VO2MAX_POWER',
  'VO2MAX_PACE',
  'POWER_3MIN',
  'POWER_12MIN',
  'WEIGHT_KG',
  'CUSTOM',
];
const METRICS: RankingMetric[] = ['TIME_OF_SEGMENT', 'SUM_OF_TIMES', 'COMPUTED_REFERENCE'];

@Component({
  selector: 'app-test-form-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './test-form-modal.component.html',
  styleUrl: './test-form-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestFormModalComponent implements OnInit {
  @Input() club!: ClubDetail;
  /** When null, the form creates a new test; otherwise it edits the given one. */
  @Input() test: ClubTestDetail | null = null;
  @Output() cancel = new EventEmitter<void>();
  @Output() save = new EventEmitter<CreateClubTestRequest>();

  private readonly testService = inject(ClubTestService);
  readonly presets$ = this.testService.presets$;
  private cachedPresets: TestPreset[] = [];

  readonly sports = SPORTS;
  readonly units = UNITS;
  readonly targets = TARGETS;
  readonly metrics = METRICS;

  name = '';
  description = '';
  competitionMode = false;
  rankingMetric: RankingMetric | null = null;
  rankingTarget: string | null = null;
  rankingDirection: RankingDirection = 'ASC';
  segments: TestSegment[] = [];
  rules: ReferenceUpdateRule[] = [];
  selectedPresetId = '';

  /** When true, segment/rule add+remove and sport/distance/unit edits are blocked (edit-safety). */
  hasResults = false;
  isEdit = false;
  showAdvanced = false;

  ngOnInit(): void {
    this.testService.loadPresets(this.club.id);
    this.presets$.subscribe((list) => (this.cachedPresets = list));
    if (this.test) {
      this.isEdit = true;
      this.name = this.test.name;
      this.description = this.test.description ?? '';
      this.competitionMode = this.test.competitionMode;
      this.rankingMetric = this.test.rankingMetric ?? null;
      this.rankingTarget = this.test.rankingTarget ?? null;
      this.rankingDirection = this.test.rankingDirection ?? 'ASC';
      this.segments = this.test.segments.map((s) => ({ ...s }));
      this.rules = this.test.referenceUpdates.map((r) => ({ ...r }));
      this.hasResults = this.test.hasResults;
    }
  }

  onPresetSelected(preset: TestPreset | null): void {
    if (!preset) return;
    this.segments = preset.segments.map((s) => ({ ...s }));
    this.rules = preset.referenceUpdates.map((r) => ({ ...r }));
    if (!this.name) this.name = preset.id.replace(/-/g, ' ');
  }

  selectPresetByEvent(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.selectedPresetId = id;
    if (!id) return;
    const match = this.cachedPresets.find((p) => p.id === id);
    if (match) this.onPresetSelected(match);
  }

  addSegment(): void {
    if (this.hasResults) return;
    this.segments = [
      ...this.segments,
      {
        id: '',
        order: this.segments.length,
        label: '',
        sportType: 'CYCLING',
        distanceMeters: null,
        durationSeconds: null,
        resultUnit: 'SECONDS',
      },
    ];
  }

  removeSegment(idx: number): void {
    if (this.hasResults) return;
    this.segments = this.segments.filter((_, i) => i !== idx);
  }

  addRule(): void {
    if (this.hasResults) return;
    this.rules = [
      ...this.rules,
      {
        id: '',
        target: 'FTP',
        customKey: null,
        label: '',
        unit: '',
        formulaExpression: '',
        autoApply: true,
      },
    ];
  }

  removeRule(idx: number): void {
    if (this.hasResults) return;
    this.rules = this.rules.filter((_, i) => i !== idx);
  }

  trackById(_index: number, x: { id: string }): string {
    return x.id;
  }

  sportLabelKey(s: SportType | string): string {
    return sportLabelKey(s);
  }
  unitLabelKey(u: SegmentResultUnit | string): string {
    return unitLabelKey(u);
  }
  targetLabelKey(t: ReferenceTarget | string): string {
    return targetLabelKey(t);
  }
  metricLabelKey(m: RankingMetric | string): string {
    return metricLabelKey(m);
  }

  variableHint(seg: TestSegment): string {
    if (!seg.id) return '';
    return `#seg_${seg.id}`;
  }

  submit(): void {
    if (!this.name.trim()) return;
    const req: CreateClubTestRequest = {
      name: this.name.trim(),
      description: this.description || null,
      competitionMode: this.competitionMode,
      rankingMetric: this.competitionMode ? this.rankingMetric : null,
      rankingTarget: this.competitionMode ? this.rankingTarget : null,
      rankingDirection: this.competitionMode ? this.rankingDirection : null,
      segments: this.segments,
      referenceUpdates: this.rules,
      presetId: null,
    };
    this.save.emit(req);
  }
}
