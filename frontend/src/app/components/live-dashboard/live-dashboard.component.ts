import { Component, inject, ViewChild, ElementRef, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WorkoutExecutionService } from '../../services/workout-execution.service';
import { BluetoothService } from '../../services/bluetooth.service';
import { TrainingService } from '../../services/training.service';
import { ZombieGameComponent } from '../zombie-game/zombie-game.component';
import { SessionSummaryComponent } from '../session-summary/session-summary.component';
import { PipService } from '../../services/pip.service';
import { HistoryService } from '../../services/history.service';
import { map } from 'rxjs';

@Component({
  selector: 'app-live-dashboard',
  standalone: true,
  imports: [CommonModule, ZombieGameComponent, SessionSummaryComponent],
  template: `
    <div class="live-container" *ngIf="state$ | async as state">
      <div class="top-bar">
        <div class="workout-title">{{ state.training?.title }}</div>
        <div class="top-controls">
           <button class="stop-btn" (click)="stopWorkout()">STOP WORKOUT</button>
        </div>
      </div>

      <ng-container>

      <div class="metrics-grid">
        <!-- Power Block -->
        <div class="metric-card power-block main-block">
          <div class="header">
            <span class="label">TARGET</span>
            <span class="target-value">{{ getTargetPower(state) }}W</span>
          </div>
          <div class="main-display">
            <div class="live-value" [style.color]="getPowerColor(state)">
              {{ (metrics$ | async)?.power }}
            </div>
            <div class="unit">WATTS</div>
          </div>
          <div class="footer-stats">
            <div class="stat">
              <span class="stat-label">SESS AVG</span>
              <span class="stat-value">{{ state.averages.power }}W</span>
            </div>
            <div class="stat">
              <span class="stat-label">BLOCK AVG</span>
              <span class="stat-value highlight">{{ state.currentBlockAverages.power }}W</span>
            </div>
          </div>
        </div>

        <!-- Cadence Block -->
        <div class="metric-card secondary-block">
          <div class="header"><span class="label">CADENCE</span></div>
          <div class="main-display">
            <div class="value">{{ (metrics$ | async)?.cadence }}</div>
            <div class="unit">RPM</div>
          </div>
          <div class="footer-stats">
            <div class="stat">
              <span class="stat-label">SESS</span>
              <span class="stat-value">{{ state.averages.cadence }}</span>
            </div>
            <div class="stat">
              <span class="stat-label">BLOCK</span>
              <span class="stat-value highlight">{{ state.currentBlockAverages.cadence }}</span>
            </div>
          </div>
        </div>

        <!-- Heart Rate Block -->
        <div class="metric-card secondary-block">
          <div class="header"><span class="label">HEART RATE</span></div>
          <div class="main-display">
            <div class="value hr">{{ (metrics$ | async)?.heartRate || '--' }}</div>
            <div class="unit">BPM</div>
          </div>
          <div class="footer-stats">
            <div class="stat">
              <span class="stat-label">SESS</span>
              <span class="stat-value">{{ state.averages.heartRate }}</span>
            </div>
            <div class="stat">
              <span class="stat-label">BLOCK</span>
              <span class="stat-value highlight">{{ state.currentBlockAverages.heartRate }}</span>
            </div>
          </div>
        </div>

        <!-- Speed Block -->
        <div class="metric-card secondary-block">
          <div class="header"><span class="label">SPEED</span></div>
          <div class="main-display">
            <div class="value">{{ (metrics$ | async)?.speed | number:'1.1-1' }}</div>
            <div class="unit">KM/H</div>
          </div>
          <div class="footer-stats">
            <div class="stat">
              <span class="stat-label">SESS</span>
              <span class="stat-value">{{ state.averages.speed | number:'1.1-1' }}</span>
            </div>
            <div class="stat">
              <span class="stat-label">BLOCK</span>
              <span class="stat-value highlight">{{ state.currentBlockAverages.speed | number:'1.1-1' }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Progress Section -->
      <div class="progress-section">
        <div class="block-info">
          <div class="current-block">
            <div class="block-tag">CURRENT</div>
            <div class="block-label">{{ state.training?.blocks?.[state.currentBlockIndex]?.label }}</div>
          </div>
          <div class="timer">{{ formatTime(state.remainingBlockSeconds) }}</div>
          <div class="next-block" *ngIf="state.training?.blocks?.[state.currentBlockIndex + 1] as next">
            <div class="block-tag">UP NEXT</div>
            <div class="block-label">{{ next.label }} ({{ formatTime(next.durationSeconds) }})</div>
          </div>
        </div>
        <div class="progress-bar-container">
          <div class="progress-bar" [style.width.%]="getBlockProgress(state)"></div>
        </div>
        <div class="total-progress">
          Total Elapsed: {{ formatTime(state.elapsedTotalSeconds) }}
        </div>
      </div>

      <!-- Live Graph or Game -->
      <div class="graph-area">
        <canvas #canvas [hidden]="showGame"></canvas>
        <app-zombie-game 
          *ngIf="showGame && (metrics$ | async) as metrics"
          [targetPower]="getTargetPower(state)"
          [currentPower]="metrics.power"
          (gameEnded)="showGame = false">
        </app-zombie-game>
      </div>

      <!-- Controls -->
      <div class="controls">
         <button class="control-btn secondary" (click)="togglePip(state)">
           {{ isPipActive ? 'CLOSE POP-OUT' : '📺 POP OUT METRICS' }}
         </button>
         <button class="control-btn secondary" (click)="toggleGame()">
           {{ showGame ? 'HIDE GAME' : 'START MINI-GAME' }}
         </button>
         <button class="control-btn secondary" (click)="skipBlock()">SKIP STEP</button>
         <button class="control-btn primary" (click)="togglePause(state)">
           {{ state.isPaused ? (state.elapsedTotalSeconds === 0 ? 'START WORKOUT' : 'RESUME') : 'PAUSE' }}
         </button>
      </div>
      </ng-container>

      <!-- Exit Confirmation Overlay -->
      <div class="summary-overlay top-center" *ngIf="showExitConfirm">
        <div class="summary-card confirmation-card">
          <h2>STOP WORKOUT?</h2>
          <p>The workout is now paused. Choose how to proceed:</p>
          <div class="confirm-actions">
            <button class="control-btn secondary" (click)="cancelExit()">RESUME</button>
            <button class="control-btn primary" (click)="saveAndSync()">SAVE & SYNC</button>
            <button class="control-btn danger" (click)="discardSession()">DISCARD</button>
          </div>
        </div>
      </div>

      <!-- Session Summary Overlay -->
      <app-session-summary 
        *ngIf="state.finalSummary" 
        [summary]="state.finalSummary"
        (close)="closeSummary()">
      </app-session-summary>
    </div>
  `,
  styles: [`
    .live-container {
      position: fixed;
      inset: 20px;
      z-index: 2000;
      display: flex;
      flex-direction: column;
      padding: 30px;
      animation: fadeIn 0.3s ease-out;
      background: #111; /* Solid dark background instead of glass */
      border: 1px solid var(--glass-border);
      border-radius: 20px;
    }
    @keyframes fadeIn { from { opacity: 0; transform: scale(0.98); } to { opacity: 1; transform: scale(1); } }

    .top-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 40px;
    }
    .workout-title { font-size: 24px; font-weight: 700; color: white; }
    .stop-btn {
      background: rgba(231, 76, 60, 0.2);
      border: 1px solid #e74c3c;
      color: #e74c3c;
      padding: 8px 16px;
      border-radius: 8px;
      cursor: pointer;
      font-weight: 600;
      transition: all 0.2s;
    }
    .stop-btn:hover { background: #e74c3c; color: white; }

    .top-controls { display: flex; align-items: center; gap: 20px; }

    .metrics-grid {
      display: grid;
      grid-template-columns: 1.4fr 1fr 1fr 1fr;
      gap: 12px;
      margin-bottom: 20px;
      height: 180px;
    }
    .metric-card {
      background: rgba(255, 255, 255, 0.05);
      border-radius: 12px;
      padding: 12px;
      display: flex;
      flex-direction: column;
      border: 1px solid var(--glass-border);
    }
    .main-block { background: rgba(255, 255, 255, 0.08); }
    
    .header { display: flex; justify-content: space-between; align-items: baseline; width: 100%; margin-bottom: auto; }
    .label { font-size: 10px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; }
    
    .main-display { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; overflow: hidden; }
    .live-value { font-size: 48px; font-weight: 800; line-height: 1; }
    .secondary-block .value { font-size: 32px; font-weight: 800; color: white; }
    .unit { font-size: 9px; font-weight: 700; color: var(--text-muted); margin-top: 2px; }
    
    .footer-stats { display: flex; justify-content: space-around; width: 100%; margin-top: auto; padding-top: 8px; border-top: 1px solid rgba(255,255,255,0.05); }
    .stat { display: flex; flex-direction: column; align-items: center; }
    .stat-label { font-size: 8px; color: var(--text-muted); margin-bottom: 2px; }
    .stat-value { font-size: 14px; font-weight: 700; color: white; }
    .stat-value.highlight { color: var(--accent-color); }
    .stat-value.hr { color: #e74c3c; }

    .target-value { font-size: 16px; color: var(--accent-color); font-weight: 700; }
    .value.hr { color: #e74c3c; }

    .progress-section { margin-bottom: 20px; }
    .block-info { display: grid; grid-template-columns: 1fr 120px 1fr; align-items: center; gap: 20px; margin-bottom: 8px; }
    .current-block { text-align: left; overflow: hidden; }
    .next-block { text-align: right; overflow: hidden; }
    .block-tag { font-size: 9px; font-weight: 700; color: var(--accent-color); margin-bottom: 2px; }
    .block-label { font-size: 14px; font-weight: 600; color: white; white-space: nowrap; text-overflow: ellipsis; overflow: hidden; }
    .timer { font-size: 32px; font-weight: 800; color: white; font-variant-numeric: tabular-nums; text-align: center; }
    .progress-bar-container {
      height: 8px;
      background: rgba(255, 255, 255, 0.1);
      border-radius: 4px;
      overflow: hidden;
      margin-bottom: 8px;
    }
    .progress-bar {
      height: 100%;
      background: var(--accent-gradient);
      transition: width 0.3s ease-out;
    }
    .total-progress { font-size: 14px; color: var(--text-muted); text-align: right; }

    .secondary-metrics {
      display: none;
    }
    .metric-item { display: flex; align-items: baseline; gap: 10px; }
    .metric-item .label { font-size: 12px; color: var(--text-muted); }
    .metric-item .value { font-size: 18px; font-weight: 700; color: white; }
    .value.highlight { color: var(--accent-color); }

    .graph-area {
      flex: 1;
      background: rgba(0, 0, 0, 0.2);
      border-radius: 20px;
      margin-bottom: 30px;
      overflow: hidden;
      position: relative;
    }
    canvas { width: 100%; height: 100%; }

    .controls { display: flex; justify-content: center; gap: 20px; }
    .control-btn {
      padding: 16px 40px;
      border-radius: 30px;
      font-weight: 700;
      font-size: 16px;
      border: none;
      cursor: pointer;
      transition: all 0.2s;
    }
    .control-btn.primary { background: white; color: black; }
    .control-btn.secondary { background: rgba(255,255,255,0.1); color: white; border: 1px solid var(--glass-border); }
    .control-btn:hover { transform: scale(1.05); }
    .control-btn.danger { background: #e74c3c; color: white; border: none; }
    
    .top-center { align-items: flex-start; padding-top: 100px; }
    .confirmation-card { padding: 40px; width: 600px; }
    .confirmation-card h2 { color: var(--accent-color); margin-bottom: 10px; font-size: 24px; font-weight: 800; }
    .confirmation-card p { color: var(--text-muted); margin-bottom: 30px; font-size: 16px; }
    .confirm-actions { display: flex; gap: 20px; justify-content: center; }
  `]
})
export class LiveDashboardComponent implements AfterViewInit, OnDestroy {
  private executionService = inject(WorkoutExecutionService);
  private bluetoothService = inject(BluetoothService);
  private trainingService = inject(TrainingService);

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
    const nextStepPower = nextStepBlock ? Math.round((ftp * (nextStepBlock.powerTargetPercent || 0)) / 100) : 0;

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
      ctx.fillText(`${val}W`, padding.left - 8, y + 4);
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
      const nextStepPower = nextStepBlock ? Math.round((ftp * (nextStepBlock.powerTargetPercent || 0)) / 100) : 0;

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

  formatTime(seconds: number): string {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  getBlockProgress(state: any): number {
    if (!state.training) return 0;
    const block = state.training.blocks[state.currentBlockIndex];
    return ((block.durationSeconds - state.remainingBlockSeconds) / block.durationSeconds) * 100;
  }

  getTargetPower(state: any): number {
    if (!state.training) return 0;
    const block = state.training.blocks[state.currentBlockIndex];
    const ftp = this.trainingService['ftpSubject'].value;

    if (block.type === 'RAMP') {
      // Simple interpolation for ramp
      const progress = (block.durationSeconds - state.remainingBlockSeconds) / block.durationSeconds;
      const start = block.powerStartPercent || 0;
      const end = block.powerEndPercent || 0;
      return Math.round(((start + (end - start) * progress) * ftp) / 100);
    }

    return Math.round(((block.powerTargetPercent || 0) * ftp) / 100);
  }

  getPowerColor(state: any): string {
    const target = this.getTargetPower(state);
    const current = this.bluetoothService['metricsSubject'].value.power;
    const diff = Math.abs(target - current);

    if (diff < 10) return '#2ecc71'; // Close
    if (diff < 30) return '#f1c40f'; // Warning
    return '#e74c3c'; // Off
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
