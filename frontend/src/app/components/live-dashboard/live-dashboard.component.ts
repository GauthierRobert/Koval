import { Component, inject, ViewChild, ElementRef, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WorkoutExecutionService } from '../../services/workout-execution.service';
import { BluetoothService } from '../../services/bluetooth.service';
import { TrainingService } from '../../services/training.service';
import { ZombieGameComponent } from '../zombie-game/zombie-game.component';
import { SessionSummaryComponent } from '../session-summary/session-summary.component';
import { PipService } from '../../services/pip.service';
import { HistoryService } from '../../services/history.service';
import { AuthService } from '../../services/auth.service';
import { map } from 'rxjs';

@Component({
  selector: 'app-live-dashboard',
  standalone: true,
  imports: [CommonModule, ZombieGameComponent, SessionSummaryComponent],
  templateUrl: './live-dashboard.component.html',
  styleUrl: './live-dashboard.component.css'
})
export class LiveDashboardComponent implements AfterViewInit, OnDestroy {
  private executionService = inject(WorkoutExecutionService);
  private bluetoothService = inject(BluetoothService);
  private trainingService = inject(TrainingService);
  private authService = inject(AuthService);

  state$ = this.executionService.state$;
  metrics$ = this.bluetoothService.metrics$;
  ftp$ = this.trainingService.ftp$;
  showGame = false;
  isPipActive = false;
  showExitConfirm = false;
  private historyService = inject(HistoryService);
  private pipService = inject(PipService);

  async togglePip(state: any) {
    const metrics = this.bluetoothService['metricsSubject'].value;
    const training = state.training;
    const blockLabel = training?.blocks?.[state.currentBlockIndex]?.label || 'WORKOUT';
    const nextStepBlock = training?.blocks?.[state.currentBlockIndex + 1];
    const nextStep = nextStepBlock?.label || (nextStepBlock ? 'RECOVERY' : 'FINISH');
    const ftp = this.trainingService['ftpSubject'].value;
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

    // Wire PiP controls
    this.pipService.onPlay.subscribe(() => {
      const state = this.executionService['stateSubject'].value;
      if (state.isPaused) this.togglePause(state);
    });
    this.pipService.onPause.subscribe(() => {
      const state = this.executionService['stateSubject'].value;
      if (!state.isPaused) this.togglePause(state);
    });
    this.pipService.onStop.subscribe(() => {
      this.stopWorkout();
    });
  }

  resizeCanvas() {
    const parent = this.canvas.nativeElement.parentElement!;
    this.canvas.nativeElement.width = parent.clientWidth;
    this.canvas.nativeElement.height = parent.clientHeight;
  }

  drawGraph() {
    const state = this.executionService['stateSubject'].value;
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
      const metrics = this.bluetoothService['metricsSubject'].value;
      const state = this.executionService['stateSubject'].value;
      const training = state.training;
      const blockLabel = training?.blocks?.[state.currentBlockIndex]?.label || 'WORKOUT';
      const nextStepBlock = training?.blocks?.[state.currentBlockIndex + 1];
      const nextStep = nextStepBlock?.label || (nextStepBlock ? 'RECOVERY' : 'FINISH');
      const ftp = (this.trainingService as any).ftpSubject.value;
      const nextStepPower = nextStepBlock ? Math.round((ftp * (nextStepBlock.intensityTarget || 0)) / 100) : 0;

      this.pipService.updateCanvas({
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
      });
    }
  }

  ngOnDestroy() {
    if (this.animationFrame) cancelAnimationFrame(this.animationFrame);
  }

  formatTime(seconds: number | undefined): string {
    if (seconds === undefined || seconds === null) return '0:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  getBlockProgress(state: any): number {
    if (!state.training) return 0;
    const block = state.training.blocks[state.currentBlockIndex];
    const duration = block.durationSeconds || 0;
    if (duration === 0) return 0;
    return ((duration - state.remainingBlockSeconds) / duration) * 100;
  }

  getTargetPower(state: any): number {
    if (!state.training) return 0;
    const block = state.training.blocks[state.currentBlockIndex];
    if (state.training.sportType !== 'CYCLING') return this.getCurrentTargetIntensity(state);
    const ftp = this.trainingService['ftpSubject'].value;

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
    const block = state.training.blocks[state.currentBlockIndex];
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
      const ftp = user?.ftp || 250;
      return Math.round((percent * ftp) / 100).toString();
    }

    if (training.sportType === 'RUNNING') {
      const threshold = user?.functionalThresholdPace || 240;
      const secondsPerKm = threshold / (percent / 100);
      return this.formatPace(secondsPerKm);
    }

    if (training.sportType === 'SWIMMING') {
      const threshold = user?.criticalSwimSpeed || 90;
      const secondsPer100m = threshold / (percent / 100);
      return this.formatPace(secondsPer100m);
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
    if (!training) return 'POWER';
    if (training.sportType === 'CYCLING') return 'WATTS';
    if (training.sportType === 'RUNNING' || training.sportType === 'SWIMMING') return 'PACE';
    return 'INTENSITY';
  }

  formatPace(totalSeconds: number): string {
    const m = Math.floor(totalSeconds / 60);
    const s = Math.round(totalSeconds % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  getPowerColor(state: any): string {
    const target = this.getTargetPower(state);
    const current = this.bluetoothService['metricsSubject'].value.power;
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
    const state = this.executionService['stateSubject'].value;
    if (state.finalSummary) {
      this.historyService.saveSession(state.finalSummary);
    }
  }

  discardSession() {
    this.showExitConfirm = false;
    // Stop without saving
    this.executionService['stateSubject'].next({
      ...this.executionService['stateSubject'].value,
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
    this.executionService['stateSubject'].next({
      ...this.executionService['stateSubject'].value,
      finalSummary: null
    });
  }
}
