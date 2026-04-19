import {AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, OnDestroy, ViewChild} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {ActivatedRoute} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {WorkoutExecutionService} from '../../../services/workout-execution.service';
import {BluetoothService} from '../../../services/bluetooth.service';
import {TrainingService} from '../../../services/training.service';
import {ZombieGameComponent} from './zombie-game/zombie-game.component';
import {SessionSummaryComponent} from './session-summary/session-summary.component';
import {PipService} from '../../../services/pip.service';
import {HistoryService} from '../../../services/history.service';
import {AuthService} from '../../../services/auth.service';
import {formatPace, formatTimeMS} from '../../shared/format/format.utils';
import {filter, take} from 'rxjs/operators';

@Component({
  selector: 'app-live-dashboard',
  standalone: true,
  imports: [CommonModule, TranslateModule, ZombieGameComponent, SessionSummaryComponent],
  templateUrl: './live-dashboard.component.html',
  styleUrl: './live-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LiveDashboardComponent implements AfterViewInit, OnDestroy {
  private translate = inject(TranslateService);
  private executionService = inject(WorkoutExecutionService);
  private bluetoothService = inject(BluetoothService);
  private trainingService = inject(TrainingService);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);

  state$ = this.executionService.state$;
  metrics$ = this.bluetoothService.metrics$;
  ftp$ = this.trainingService.ftp$;
  showGame = false;
  isPipActive = false;
  showExitConfirm = false;
  private historyService = inject(HistoryService);
  private pipService = inject(PipService);
  private destroyRef = inject(DestroyRef);

  async togglePip(state: any) {
    const metrics = this.bluetoothService.currentMetrics;
    const training = state.training;
    const blockLabel = training?.blocks?.[state.currentBlockIndex]?.label || 'WORKOUT';
    const nextStepBlock = training?.blocks?.[state.currentBlockIndex + 1];
    const nextStep = nextStepBlock?.label || (nextStepBlock ? 'RECOVERY' : 'FINISH');
    const ftp = this.trainingService.currentFtp ?? 250;
    const nextStepPower = nextStepBlock ? Math.round((ftp * (nextStepBlock.intensityTarget || 0)) / 100) : 0;

    const data = {
      power: metrics.power,
      target: this.getTargetPower(state),
      hr: metrics.heartRate || 0,
      time: this.formatTime(state.remainingBlockSeconds),
      color: this.getPowerColor(state),
      blockLabel: blockLabel,
      nextStepLabel: nextStep,
      nextStepPower: nextStepPower,
      totalTime: this.formatTime(state.elapsedTotalSeconds),
      isPaused: state.isPaused
    };
    await this.pipService.togglePip(data);
    this.isPipActive = this.pipService.isPipActive;
  }

  @ViewChild('canvas') canvas!: ElementRef<HTMLCanvasElement>;
  private ctx!: CanvasRenderingContext2D;
  private animationFrame?: number;

  ngAfterViewInit() {
    this.ctx = this.canvas.nativeElement.getContext('2d')!;
    this.resizeCanvas();
    this.drawGraph();

    // Demo mode: ?demo=1 auto-loads a workout and toggles bluetooth simulation.
    // Used by the promo capture spec; also exposes an entry point for first-time users
    // who don't own a physical trainer.
    if (this.route.snapshot.queryParamMap.get('demo') === '1') {
      this.activateDemoMode();
    }

    // Wire PiP controls
    this.pipService.onPlay.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      const state = this.executionService.currentState;
      if (state.isPaused) this.togglePause(state);
    });
    this.pipService.onPause.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      const state = this.executionService.currentState;
      if (!state.isPaused) this.togglePause(state);
    });
    this.pipService.onStop.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.stopWorkout();
    });
  }

  resizeCanvas() {
    const parent = this.canvas.nativeElement.parentElement!;
    this.canvas.nativeElement.width = parent.clientWidth;
    this.canvas.nativeElement.height = parent.clientHeight;
  }

  drawGraph() {
    const state = this.executionService.currentState;
    const canvas = this.canvas.nativeElement;
    const ctx = this.ctx;
    const padding = { left: 40, right: 10, top: 10, bottom: 20 };
    const chartWidth = canvas.width - padding.left - padding.right;
    const chartHeight = canvas.height - padding.top - padding.bottom;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Draw Y-Axis Labels & Grid
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.font = '10px Inter';
    ctx.textAlign = 'right';
    ctx.lineWidth = 1;

    const maxPower = 400;
    const ySteps = 4;
    for (let i = 0; i <= ySteps; i++) {
      const val = (maxPower / ySteps) * i;
      const y = padding.top + chartHeight - (val / maxPower) * chartHeight;

      // Grid Line
      ctx.beginPath();
      ctx.moveTo(padding.left, y);
      ctx.lineTo(padding.left + chartWidth, y);
      ctx.stroke();

      // Label
      const label = state.training?.sportType === 'CYCLING' ? `${val}W` : val.toString() + '%';
      ctx.fillText(label, padding.left - 8, y + 4);
    }

    // Draw Base Line (X-Axis)
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.3)';
    ctx.beginPath();
    ctx.moveTo(padding.left, padding.top + chartHeight);
    ctx.lineTo(padding.left + chartWidth, padding.top + chartHeight);
    ctx.stroke();

    if (state.history.length > 0) {
      ctx.beginPath();
      ctx.strokeStyle = 'var(--accent-color)';
      ctx.lineWidth = 2;

      const step = chartWidth / Math.max(state.history.length, 300);

      state.history.forEach((m, i) => {
        const x = padding.left + i * step;
        const normalizedPower = Math.min(m.power, maxPower);
        const y = padding.top + chartHeight - (normalizedPower / maxPower) * chartHeight;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.stroke();

      // Fill secondary area
      ctx.lineTo(padding.left + (state.history.length - 1) * step, padding.top + chartHeight);
      ctx.lineTo(padding.left, padding.top + chartHeight);
      ctx.fillStyle = 'rgba(52, 152, 219, 0.1)';
      ctx.fill();
    }

    this.animationFrame = requestAnimationFrame(() => this.drawGraph());

    // Also update PiP if active
    if (this.isPipActive) {
      const metrics = this.bluetoothService.currentMetrics;
      const pipState = this.executionService.currentState;
      const training = pipState.training;
      const blockLabel = training?.blocks?.[pipState.currentBlockIndex]?.label || 'WORKOUT';
      const nextStepBlock = training?.blocks?.[pipState.currentBlockIndex + 1];
      const nextStep = nextStepBlock?.label || (nextStepBlock ? 'RECOVERY' : 'FINISH');
      const ftp = this.trainingService.currentFtp ?? 250;
      const nextStepPower = nextStepBlock ? Math.round((ftp * (nextStepBlock.intensityTarget || 0)) / 100) : 0;

      this.pipService.updateCanvas({
        power: metrics.power,
        target: this.getTargetPower(pipState),
        hr: metrics.heartRate || 0,
        time: this.formatTime(pipState.remainingBlockSeconds),
        color: this.getPowerColor(pipState),
        blockLabel: blockLabel,
        nextStepLabel: nextStep,
        nextStepPower: nextStepPower,
        totalTime: this.formatTime(pipState.elapsedTotalSeconds),
        isPaused: pipState.isPaused
      });
    }
  }

  ngOnDestroy() {
    if (this.animationFrame) cancelAnimationFrame(this.animationFrame);
  }

  private activateDemoMode(): void {
    this.bluetoothService.toggleSimulation(true);
    if (this.executionService.currentState.training) return;
    this.trainingService.trainings$
      .pipe(filter((list) => list.length > 0), take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((list) => {
        const cycling = list.find((t) => t.sportType === 'CYCLING') ?? list[0];
        this.executionService.startWorkout(cycling);
      });
  }

  formatTime(seconds: number | undefined): string {
    return formatTimeMS(seconds);
  }

  getBlockProgress(state: any): number {
    if (!state.training) return 0;
    const block = state.flatBlocks[state.currentBlockIndex];
    const duration = block.durationSeconds || 0;
    if (duration === 0) return 0;
    return ((duration - state.remainingBlockSeconds) / duration) * 100;
  }

  getTargetPower(state: any): number {
    if (!state.training) return 0;
    const block = state.flatBlocks[state.currentBlockIndex];
    if (state.training.sportType !== 'CYCLING') return this.getCurrentTargetIntensity(state);
    const ftp = this.trainingService.currentFtp ?? 250;

    if (block.type === 'RAMP') {
      const duration = block.durationSeconds || 1;
      const progress = (duration - state.remainingBlockSeconds) / duration;
      const start = block.intensityStart || 0;
      const end = block.intensityEnd || 0;
      return Math.round(((start + (end - start) * progress) * ftp) / 100);
    }

    return Math.round(((block.intensityTarget || 0) * ftp) / 100);
  }

  getCurrentTargetIntensity(state: any): number {
    if (!state.training) return 0;
    const block = state.flatBlocks[state.currentBlockIndex];
    if (block.type === 'RAMP') {
      const duration = block.durationSeconds || 1;
      const progress = (duration - state.remainingBlockSeconds) / duration;
      const start = block.intensityStart || 0;
      const end = block.intensityEnd || 0;
      return start + (end - start) * progress;
    }
    return block.intensityTarget || 0;
  }

  calculateIntensityValue(percent: number | undefined, training: any): string {
    if (percent === undefined || !training) return '0';
    const user = this.authService.currentUser;

    if (training.sportType === 'CYCLING') {
      if (!user?.ftp) return `${percent}%`;
      return Math.round((percent * user.ftp) / 100).toString();
    }

    if (training.sportType === 'RUNNING') {
      if (!user?.functionalThresholdPace) return `${percent}%`;
      const secondsPerKm = user.functionalThresholdPace / (percent / 100);
      return this.formatPaceValue(secondsPerKm);
    }

    if (training.sportType === 'SWIMMING') {
      if (!user?.criticalSwimSpeed) return `${percent}%`;
      const secondsPer100m = user.criticalSwimSpeed / (percent / 100);
      return this.formatPaceValue(secondsPer100m);
    }

    return percent.toString();
  }

  getSportUnit(training: any): string {
    if (!training) return '';
    if (training.sportType === 'CYCLING') return 'W';
    if (training.sportType === 'RUNNING') return '/km';
    if (training.sportType === 'SWIMMING') return '/100m';
    return '%';
  }

  getSportLabel(training: any): string {
    if (!training) return this.translate.instant('LIVE_SESSION.SPORT_LABEL_POWER');
    if (training.sportType === 'CYCLING') return this.translate.instant('LIVE_SESSION.SPORT_LABEL_WATTS');
    if (training.sportType === 'RUNNING' || training.sportType === 'SWIMMING') return this.translate.instant('LIVE_SESSION.SPORT_LABEL_PACE');
    return this.translate.instant('LIVE_SESSION.SPORT_LABEL_INTENSITY');
  }

  formatPaceValue(totalSeconds: number): string {
    return formatPace(totalSeconds);
  }

  getPowerColor(state: any): string {
    const target = this.getTargetPower(state);
    const current = this.bluetoothService.currentMetrics.power;
    const diff = Math.abs(target - current);

    if (diff < 10) return '#34d399'; // Close
    if (diff < 30) return '#fbbf24'; // Warning
    return '#f87171'; // Off
  }

  async togglePause(state: any) {
    this.executionService.togglePause();
    // Sync PiP state immediately if active
    if (this.isPipActive) {
      await this.pipService.updatePlaybackState(!state.isPaused);
    }
  }

  skipBlock() {
    this.executionService.skipBlock();
  }

  toggleGame() {
    this.showGame = !this.showGame;
  }

  stopWorkout() {
    this.executionService.pauseWorkout();
    this.showExitConfirm = true;
  }

  cancelExit() {
    this.showExitConfirm = false;
    this.executionService.resumeWorkout();
  }

  saveAndSync() {
    this.showExitConfirm = false;
    this.executionService.stopWorkout();
    if (this.isPipActive) {
      this.pipService.togglePip(null as any);
      this.isPipActive = false;
    }
    // Save to history when summary is generated
    const currentState = this.executionService.currentState;
    if (currentState.finalSummary) {
      this.historyService.saveSession(currentState.finalSummary);
    }
  }

  discardSession() {
    this.showExitConfirm = false;
    // Stop without saving
    this.executionService.updateState({
      isActive: false,
      isPaused: false,
      finalSummary: null
    });
    if (this.isPipActive) {
      this.pipService.togglePip(null as any);
      this.isPipActive = false;
    }
  }

  closeSummary() {
    this.executionService.updateState({
      finalSummary: null
    });
  }
}
