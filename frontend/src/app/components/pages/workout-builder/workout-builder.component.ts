import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { BehaviorSubject } from 'rxjs';
import { filter, map, take } from 'rxjs/operators';
import { TrainingService } from '../../../services/training.service';
import { AuthService } from '../../../services/auth.service';
import { ErrorToastService } from '../../../services/error-toast.service';
import {
  isSet,
  StrokeType,
  SwimEquipment,
  Training,
  TrainingType,
  TRAINING_TYPES,
  WorkoutBlock,
} from '../../../models/training.model';
import { BlockEditorFormComponent } from './block-editor-form/block-editor-form.component';
import { SetEditorFormComponent } from './set-editor-form/set-editor-form.component';
import { BlockListItemComponent } from './block-list-item/block-list-item.component';
import { SetBlockItemComponent } from './set-block-item/set-block-item.component';
import { WorkoutChartBarComponent } from '../../shared/workout-visualization/workout-chart-bar/workout-chart-bar.component';
import { getBlockColor as sharedGetBlockColor, formatBlockSize } from '../../shared/block-helpers/block-helpers';
import {
  applySetEditToBlock,
  BlockEditFormState,
  blockToFormState,
  blockToSetEditState,
  buildBlockFromForm,
  buildTrainingSavePayload,
  computeBlockDuration,
  computeBlockTss,
  createSetBlock,
  defaultFormState,
  formatBlockSeconds,
  formatTotalDuration,
  mergeFormIntoBlock,
  removeChildFromSet,
} from './workout-builder-helpers';
type SportType = 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK';
type BlockType = WorkoutBlock['type'];

@Component({
  selector: 'app-workout-builder',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    TranslateModule,
    DragDropModule,
    BlockEditorFormComponent,
    SetEditorFormComponent,
    BlockListItemComponent,
    SetBlockItemComponent,
    WorkoutChartBarComponent,
  ],
  templateUrl: './workout-builder.component.html',
  styleUrl: './workout-builder.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutBuilderComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private trainingService = inject(TrainingService);
  private authService = inject(AuthService);
  private destroyRef = inject(DestroyRef);
  private toast = inject(ErrorToastService);

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
  saving = false;

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
  editStrokeType: StrokeType | null = null;
  editEquipment: SwimEquipment[] = [];
  editSendOffSeconds: number | null = null;

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

  readonly BLOCK_TYPES: BlockType[] = ['WARMUP', 'STEADY', 'INTERVAL', 'RAMP', 'COOLDOWN', 'FREE', 'PAUSE', 'TRANSITION'];
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
    this.trainingService.loadTrainings();
    this.trainingService.trainings$
      .pipe(
        map((trainings) => trainings.find((t) => t.id === id)),
        filter((training): training is Training => !!training),
        take(1),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((training) => {
        this.title = training.title;
        this.description = training.description || '';
        this.sportType = training.sportType;
        this.trainingType = training.trainingType || 'MIXED';
        this.blocksSubject.next([...(training.blocks || [])]);
      });
  }

  get blocks(): WorkoutBlock[] {
    return this.blocksSubject.value;
  }

  /** Synthetic Training reflecting the current builder state for live preview. */
  get previewTraining(): Training {
    return {
      id: 'builder-preview',
      title: this.title,
      description: this.description,
      blocks: this.blocks,
      sportType: this.sportType,
      trainingType: this.trainingType,
      estimatedTss: this.estimatedTss,
      estimatedDurationSeconds: this.estimatedDuration,
    };
  }

  // ── Block management ──────────────────────────────────────────────

  private currentFormState(): BlockEditFormState {
    return {
      editType: this.editType,
      editLabel: this.editLabel,
      editDurationMin: this.editDurationMin,
      editDurationSec: this.editDurationSec,
      editDistanceMeters: this.editDistanceMeters,
      editIntensity: this.editIntensity,
      editIntensityStart: this.editIntensityStart,
      editIntensityEnd: this.editIntensityEnd,
      editCadence: this.editCadence,
      editZone: this.editZone,
      editStrokeType: this.editStrokeType,
      editEquipment: this.editEquipment,
      editSendOffSeconds: this.editSendOffSeconds,
    };
  }

  addBlock(): void {
    const block = buildBlockFromForm(this.currentFormState(), this.sportType);
    this.blocksSubject.next([...this.blocks, block]);
    this.resetBlockForm();
  }

  updateBlock(): void {
    if (this.selectedBlockIndex < 0) return;
    const blocks = [...this.blocks];
    const current = blocks[this.selectedBlockIndex];
    const form = this.currentFormState();

    if (this.selectedChildIndex >= 0 && isSet(current)) {
      const elements = [...(current.elements ?? [])];
      elements[this.selectedChildIndex] = mergeFormIntoBlock(
        elements[this.selectedChildIndex],
        form,
        this.sportType,
      );
      blocks[this.selectedBlockIndex] = {...current, elements};
    } else if (isSet(current)) {
      blocks[this.selectedBlockIndex] = applySetEditToBlock(current, {
        editSetReps: this.editSetReps,
        editSetNoRest: this.editSetNoRest,
        editSetRestDuration: this.editSetRestDuration,
        editSetPassiveRest: this.editSetPassiveRest,
        editSetRestIntensity: this.editSetRestIntensity,
        editLabel: this.editLabel,
      });
    } else {
      blocks[this.selectedBlockIndex] = mergeFormIntoBlock(current, form, this.sportType);
    }
    this.blocksSubject.next(blocks);
  }

  /** Auto-commit edits to the selected block on every form change. No-op when nothing is selected. */
  autoUpdateBlock(): void {
    if (this.selectedBlockIndex < 0) return;
    this.updateBlock();
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
    // Re-clicking the currently-selected block deselects it.
    if (this.selectedBlockIndex === index && this.selectedChildIndex === -1) {
      this.selectedBlockIndex = -1;
      this.resetBlockForm();
      return;
    }
    this.selectedBlockIndex = index;
    this.selectedChildIndex = -1;
    const block = this.blocks[index];
    if (!block) return;

    if (isSet(block)) {
      const s = blockToSetEditState(block);
      this.editSetReps = s.editSetReps;
      this.editSetNoRest = s.editSetNoRest;
      this.editSetRestDuration = s.editSetRestDuration;
      this.editSetPassiveRest = s.editSetPassiveRest;
      this.editSetRestIntensity = s.editSetRestIntensity;
      this.editLabel = s.editLabel;
    } else {
      this.populateBlockForm(block);
    }
  }

  selectChildBlock(parentIndex: number, childIndex: number): void {
    // Re-clicking the currently-selected child deselects it.
    if (this.selectedBlockIndex === parentIndex && this.selectedChildIndex === childIndex) {
      this.selectedBlockIndex = -1;
      this.selectedChildIndex = -1;
      this.resetBlockForm();
      return;
    }
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
    if (!parent) return;
    const updated = removeChildFromSet(parent, childIndex);
    if (updated === null) {
      blocks.splice(parentIndex, 1);
      this.selectedBlockIndex = -1;
      this.selectedChildIndex = -1;
      this.resetBlockForm();
    } else {
      blocks[parentIndex] = updated;
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
    this.applyFormState(blockToFormState(block));
  }

  private applyFormState(s: BlockEditFormState): void {
    this.editType = s.editType;
    this.editLabel = s.editLabel;
    this.editDurationMin = s.editDurationMin;
    this.editDurationSec = s.editDurationSec;
    this.editDistanceMeters = s.editDistanceMeters;
    this.editIntensity = s.editIntensity;
    this.editIntensityStart = s.editIntensityStart;
    this.editIntensityEnd = s.editIntensityEnd;
    this.editCadence = s.editCadence;
    this.editZone = s.editZone;
    this.editStrokeType = s.editStrokeType;
    this.editEquipment = s.editEquipment;
    this.editSendOffSeconds = s.editSendOffSeconds;
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
    const set = createSetBlock(
      selectedBlocks,
      this.setReps,
      this.setNoRest,
      this.setRestDuration,
      this.setRestIntensity,
      this.setPassiveRest,
    );
    const blocks = this.blocks.filter((_, i) => !this.selectedForSet.has(i));
    blocks.splice(indices[0], 0, set);
    this.blocksSubject.next(blocks);
    this.selectedForSet.clear();
    this.showSetForm = false;
  }

  // ── Computed metrics ──────────────────────────────────────────────

  get estimatedTss(): number {
    return computeBlockTss(this.blocks);
  }

  get estimatedDuration(): number {
    return computeBlockDuration(this.blocks);
  }

  get formattedDuration(): string { return formatTotalDuration(this.estimatedDuration); }

  // ── Save ──────────────────────────────────────────────────────────

  save(): void {
    if (this.saving || !this.title.trim() || this.blocks.length === 0) return;
    const training = buildTrainingSavePayload({
      title: this.title,
      description: this.description,
      sportType: this.sportType,
      trainingType: this.trainingType,
      blocks: this.blocks,
    });
    this.saving = true;
    const op$ = this.isEditing && this.trainingId
      ? this.trainingService.updateTraining(this.trainingId, training)
      : this.trainingService.createTraining(training);
    op$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.saving = false;
        this.router.navigate(['/trainings', result.id]);
      },
      error: (err) => {
        this.saving = false;
        this.toast.show(err?.error?.message || err?.message || 'Failed to save training. Please try again.', 'error');
      },
    });
  }

  canSave(): boolean {
    return !this.saving && this.title.trim().length > 0 && this.blocks.length > 0;
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

  blockColor(block: WorkoutBlock): string {
    return sharedGetBlockColor(block, this.sportType);
  }

  formatSeconds(sec: number): string { return formatBlockSeconds(sec); }

  formatBlockDuration(block: WorkoutBlock): string {
    return formatBlockSize(block);
  }

  private resetBlockForm(): void {
    this.applyFormState(defaultFormState());
  }
}
