import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { BehaviorSubject } from 'rxjs';
import { TrainingService } from '../../../services/training.service';
import { AuthService } from '../../../services/auth.service';
import {
  isSet,
  Training,
  TrainingType,
  TRAINING_TYPES,
  WorkoutBlock,
} from '../../../models/training.model';
type SportType = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK';
type BlockType = WorkoutBlock['type'];

@Component({
  selector: 'app-workout-builder',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule, DragDropModule],
  templateUrl: './workout-builder.component.html',
  styleUrl: './workout-builder.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutBuilderComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private trainingService = inject(TrainingService);
  private authService = inject(AuthService);

  // Metadata
  title = '';
  description = '';
  sportType: SportType = 'CYCLING';
  trainingType: TrainingType = 'MIXED';

  // Blocks
  private blocksSubject = new BehaviorSubject<WorkoutBlock[]>([]);
  blocks$ = this.blocksSubject.asObservable();

  // Editor state
  selectedBlockIndex = -1;
  selectedChildIndex = -1; // child within a set (-1 = set itself selected)
  isEditing = false; // true when editing existing training
  trainingId: string | null = null;

  // Block form (inline editor)
  editType: BlockType = 'STEADY';
  editLabel = '';
  editDurationMin = 5;
  editDurationSec = 0;
  editDistanceMeters: number | null = null;
  editIntensity: number | null = 75;
  editIntensityStart: number | null = null;
  editIntensityEnd: number | null = null;
  editCadence: number | null = null;
  editZone = '';

  // Set form (create new set)
  showSetForm = false;
  setReps = 3;
  setNoRest = false;
  setRestDuration = 120;
  setRestIntensity = 60;
  setPassiveRest = false;
  selectedForSet: Set<number> = new Set();

  // Set editing (edit existing set)
  editSetReps = 3;
  editSetNoRest = false;
  editSetRestDuration = 120;
  editSetRestIntensity = 60;
  editSetPassiveRest = false;

  readonly BLOCK_TYPES: BlockType[] = ['WARMUP', 'STEADY', 'INTERVAL', 'RAMP', 'COOLDOWN', 'FREE', 'PAUSE'];
  readonly SPORT_TYPES: SportType[] = ['CYCLING', 'RUNNING', 'SWIMMING', 'BRICK'];
  readonly TRAINING_TYPES = TRAINING_TYPES;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.trainingId = id;
      this.isEditing = true;
      this.loadTraining(id);
    }
  }

  private loadTraining(id: string): void {
    // Use existing trainings from service
    const sub = this.trainingService.trainings$.subscribe((trainings) => {
      const training = trainings.find((t) => t.id === id);
      if (training) {
        this.title = training.title;
        this.description = training.description || '';
        this.sportType = training.sportType;
        this.trainingType = training.trainingType || 'MIXED';
        this.blocksSubject.next([...(training.blocks || [])]);
        sub.unsubscribe();
      }
    });
  }

  get blocks(): WorkoutBlock[] {
    return this.blocksSubject.value;
  }

  // ── Block management ──────────────────────────────────────────────

  addBlock(): void {
    const block: WorkoutBlock = {
      type: this.editType,
      label: this.editLabel || this.editType,
      durationSeconds: (this.editDurationMin || 0) * 60 + (this.editDurationSec || 0),
      ...(this.editDistanceMeters ? { distanceMeters: this.editDistanceMeters } : {}),
      ...(this.editType === 'RAMP'
        ? { intensityStart: this.editIntensityStart ?? undefined, intensityEnd: this.editIntensityEnd ?? undefined }
        : this.editType !== 'FREE' && this.editType !== 'PAUSE'
          ? { intensityTarget: this.editIntensity ?? undefined }
          : {}),
      ...(this.editCadence ? { cadenceTarget: this.editCadence } : {}),
      ...(this.editZone ? { zoneTarget: this.editZone } : {}),
    };

    const blocks = [...this.blocks, block];
    this.blocksSubject.next(blocks);
    this.resetBlockForm();
  }

  updateBlock(): void {
    if (this.selectedBlockIndex < 0) return;
    const blocks = [...this.blocks];
    const current = blocks[this.selectedBlockIndex];

    if (this.selectedChildIndex >= 0 && isSet(current)) {
      // Updating a child element within a set
      const elements = [...(current.elements ?? [])];
      elements[this.selectedChildIndex] = {
        ...elements[this.selectedChildIndex],
        type: this.editType,
        label: this.editLabel || this.editType,
        durationSeconds: (this.editDurationMin || 0) * 60 + (this.editDurationSec || 0),
        distanceMeters: this.editDistanceMeters ?? undefined,
        ...(this.editType === 'RAMP'
          ? { intensityStart: this.editIntensityStart ?? undefined, intensityEnd: this.editIntensityEnd ?? undefined, intensityTarget: undefined }
          : this.editType !== 'FREE' && this.editType !== 'PAUSE'
            ? { intensityTarget: this.editIntensity ?? undefined, intensityStart: undefined, intensityEnd: undefined }
            : { intensityTarget: undefined, intensityStart: undefined, intensityEnd: undefined }),
        cadenceTarget: this.editCadence ?? undefined,
        zoneTarget: this.editZone || undefined,
      };
      blocks[this.selectedBlockIndex] = { ...current, elements };
    } else if (isSet(current)) {
      blocks[this.selectedBlockIndex] = {
        ...current,
        label: this.editLabel || `${this.editSetReps}x Set`,
        repetitions: this.editSetReps,
        restDurationSeconds: this.editSetNoRest ? 0 : this.editSetRestDuration,
        restIntensity: this.editSetNoRest ? 0 : this.editSetPassiveRest ? 0 : this.editSetRestIntensity,
      };
    } else {
      blocks[this.selectedBlockIndex] = {
        ...current,
        type: this.editType,
        label: this.editLabel || this.editType,
        durationSeconds: (this.editDurationMin || 0) * 60 + (this.editDurationSec || 0),
        distanceMeters: this.editDistanceMeters ?? undefined,
        ...(this.editType === 'RAMP'
          ? { intensityStart: this.editIntensityStart ?? undefined, intensityEnd: this.editIntensityEnd ?? undefined, intensityTarget: undefined }
          : this.editType !== 'FREE' && this.editType !== 'PAUSE'
            ? { intensityTarget: this.editIntensity ?? undefined, intensityStart: undefined, intensityEnd: undefined }
            : { intensityTarget: undefined, intensityStart: undefined, intensityEnd: undefined }),
        cadenceTarget: this.editCadence ?? undefined,
        zoneTarget: this.editZone || undefined,
      };
    }
    this.blocksSubject.next(blocks);
    this.selectedBlockIndex = -1;
    this.selectedChildIndex = -1;
    this.resetBlockForm();
  }

  removeBlock(index: number): void {
    const blocks = this.blocks.filter((_, i) => i !== index);
    this.blocksSubject.next(blocks);
    if (this.selectedBlockIndex === index) {
      this.selectedBlockIndex = -1;
      this.selectedChildIndex = -1;
      this.resetBlockForm();
    }
  }

  duplicateBlock(index: number): void {
    const blocks = [...this.blocks];
    blocks.splice(index + 1, 0, { ...blocks[index] });
    this.blocksSubject.next(blocks);
  }

  selectBlock(index: number): void {
    this.selectedBlockIndex = index;
    this.selectedChildIndex = -1;
    const block = this.blocks[index];
    if (!block) return;

    if (isSet(block)) {
      this.editSetReps = block.repetitions ?? 3;
      this.editSetNoRest = (block.restDurationSeconds ?? 0) === 0;
      this.editSetRestDuration = block.restDurationSeconds ?? 120;
      this.editSetPassiveRest = (block.restIntensity ?? 60) === 0;
      this.editSetRestIntensity = this.editSetPassiveRest ? 60 : (block.restIntensity ?? 60);
      this.editLabel = block.label || '';
    } else {
      this.populateBlockForm(block);
    }
  }

  selectChildBlock(parentIndex: number, childIndex: number): void {
    this.selectedBlockIndex = parentIndex;
    this.selectedChildIndex = childIndex;
    const parent = this.blocks[parentIndex];
    if (!parent?.elements?.[childIndex]) return;
    this.populateBlockForm(parent.elements[childIndex]);
  }

  deselectBlock(): void {
    if (this.selectedChildIndex >= 0) {
      // Go back to set-level selection
      this.selectedChildIndex = -1;
      this.selectBlock(this.selectedBlockIndex);
    } else {
      this.selectedBlockIndex = -1;
      this.selectedChildIndex = -1;
      this.resetBlockForm();
    }
  }

  dissociateSet(index: number): void {
    const blocks = [...this.blocks];
    const set = blocks[index];
    if (!set?.elements) return;
    blocks.splice(index, 1, ...set.elements);
    this.blocksSubject.next(blocks);
    this.selectedBlockIndex = -1;
    this.selectedChildIndex = -1;
    this.resetBlockForm();
  }

  removeChildBlock(parentIndex: number, childIndex: number): void {
    const blocks = [...this.blocks];
    const parent = blocks[parentIndex];
    if (!parent?.elements) return;
    const elements = parent.elements.filter((_, i) => i !== childIndex);
    if (elements.length === 0) {
      // Remove the set entirely if no children left
      blocks.splice(parentIndex, 1);
      this.selectedBlockIndex = -1;
      this.selectedChildIndex = -1;
      this.resetBlockForm();
    } else {
      blocks[parentIndex] = { ...parent, elements };
      if (this.selectedChildIndex === childIndex) {
        this.selectedChildIndex = -1;
        this.selectBlock(parentIndex);
      }
    }
    this.blocksSubject.next(blocks);
  }

  isSelectedBlockASet(): boolean {
    if (this.selectedBlockIndex < 0) return false;
    if (this.selectedChildIndex >= 0) return false; // editing a child, show block editor
    const block = this.blocks[this.selectedBlockIndex];
    return !!block && isSet(block);
  }

  private populateBlockForm(block: WorkoutBlock): void {
    this.editType = block.type;
    this.editLabel = block.label || '';
    const dur = block.durationSeconds || 0;
    this.editDurationMin = Math.floor(dur / 60);
    this.editDurationSec = dur % 60;
    this.editDistanceMeters = block.distanceMeters ?? null;
    this.editIntensity = block.intensityTarget ?? null;
    this.editIntensityStart = block.intensityStart ?? null;
    this.editIntensityEnd = block.intensityEnd ?? null;
    this.editCadence = block.cadenceTarget ?? null;
    this.editZone = block.zoneTarget || '';
  }

  dropBlock(event: CdkDragDrop<WorkoutBlock[]>): void {
    const blocks = [...this.blocks];
    moveItemInArray(blocks, event.previousIndex, event.currentIndex);
    this.blocksSubject.next(blocks);
  }

  // ── Set management ────────────────────────────────────────────────

  toggleSetSelection(index: number): void {
    if (this.selectedForSet.has(index)) {
      this.selectedForSet.delete(index);
    } else {
      this.selectedForSet.add(index);
    }
  }

  createSet(): void {
    if (this.selectedForSet.size < 1) return;

    const indices = Array.from(this.selectedForSet).sort((a, b) => a - b);
    const selectedBlocks = indices.map((i) => this.blocks[i]);

    const set: WorkoutBlock = {
      type: 'INTERVAL',
      label: `${this.setReps}x Set`,
      repetitions: this.setReps,
      elements: selectedBlocks,
      restDurationSeconds: this.setNoRest ? 0 : this.setRestDuration,
      restIntensity: this.setNoRest ? 0 : this.setPassiveRest ? 0 : this.setRestIntensity,
    };

    // Remove selected blocks and insert set at first index position
    const blocks = this.blocks.filter((_, i) => !this.selectedForSet.has(i));
    blocks.splice(indices[0], 0, set);
    this.blocksSubject.next(blocks);

    this.selectedForSet.clear();
    this.showSetForm = false;
  }

  // ── Computed metrics ──────────────────────────────────────────────

  get estimatedTss(): number {
    return this.computeTss(this.blocks);
  }

  get estimatedDuration(): number {
    return this.computeDuration(this.blocks);
  }

  get formattedDuration(): string {
    const total = this.estimatedDuration;
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
  }

  private computeTss(blocks: WorkoutBlock[]): number {
    let tss = 0;
    for (const block of blocks) {
      if (isSet(block)) {
        const reps = block.repetitions ?? 1;
        const childTss = this.computeTss(block.elements!);
        const restTss = (block.restDurationSeconds ?? 0) * Math.pow((block.restIntensity ?? 60) / 100, 2) / 36;
        tss += reps * childTss + (reps - 1) * restTss;
      } else {
        const dur = block.durationSeconds ?? 0;
        let pct = 50;
        if (block.type === 'RAMP') {
          pct = ((block.intensityStart ?? 50) + (block.intensityEnd ?? 50)) / 2;
        } else if (block.type !== 'FREE' && block.type !== 'PAUSE') {
          pct = block.intensityTarget ?? 50;
        }
        tss += dur * Math.pow(pct / 100, 2) / 36;
      }
    }
    return Math.round(tss);
  }

  private computeDuration(blocks: WorkoutBlock[]): number {
    let total = 0;
    for (const block of blocks) {
      if (isSet(block)) {
        const reps = block.repetitions ?? 1;
        const childDur = this.computeDuration(block.elements!);
        total += reps * childDur + (reps - 1) * (block.restDurationSeconds ?? 0);
      } else {
        total += block.durationSeconds ?? 0;
      }
    }
    return total;
  }

  // ── Save ──────────────────────────────────────────────────────────

  save(): void {
    if (!this.title.trim() || this.blocks.length === 0) return;

    const training: Partial<Training> = {
      title: this.title.trim(),
      description: this.description.trim(),
      sportType: this.sportType,
      trainingType: this.trainingType,
      blocks: this.blocks,
      estimatedTss: this.estimatedTss,
      estimatedDurationSeconds: this.estimatedDuration,
    };

    if (this.isEditing && this.trainingId) {
      this.trainingService.updateTraining(this.trainingId, training).subscribe({
        next: () => this.router.navigate(['/trainings']),
      });
    } else {
      this.trainingService.createTraining(training).subscribe({
        next: () => this.router.navigate(['/trainings']),
      });
    }
  }

  canSave(): boolean {
    return this.title.trim().length > 0 && this.blocks.length > 0;
  }

  // ── Helpers ───────────────────────────────────────────────────────

  isSet(block: WorkoutBlock): boolean {
    return isSet(block);
  }

  isRamp(): boolean {
    return this.editType === 'RAMP';
  }

  hideIntensity(): boolean {
    return this.editType === 'FREE' || this.editType === 'PAUSE';
  }

  blockColor(block: { type: string }): string {
    const colors: Record<string, string> = {
      WARMUP: '#f59e0b',
      STEADY: '#22c55e',
      INTERVAL: '#ef4444',
      RAMP: '#8b5cf6',
      COOLDOWN: '#3b82f6',
      FREE: '#6b7280',
      PAUSE: '#374151',
    };
    return colors[block.type] || '#6b7280';
  }

  formatSeconds(sec: number): string {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return s > 0 ? `${m}m${s}s` : `${m}m`;
  }

  formatBlockDuration(block: WorkoutBlock): string {
    const sec = block.durationSeconds ?? 0;
    if (sec < 60) return `${sec}s`;
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return s > 0 ? `${m}m${s}s` : `${m}m`;
  }

  private resetBlockForm(): void {
    this.editType = 'STEADY';
    this.editLabel = '';
    this.editDurationMin = 5;
    this.editDurationSec = 0;
    this.editDistanceMeters = null;
    this.editIntensity = 75;
    this.editIntensityStart = null;
    this.editIntensityEnd = null;
    this.editCadence = null;
    this.editZone = '';
  }
}
