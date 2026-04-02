import { inject, Injectable, NgZone } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { distinctUntilChanged, filter, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { BluetoothService, LiveMetrics } from './bluetooth.service';
import { WorkoutExecutionService } from './workout-execution.service';
import { TrainingService } from './training.service';
import { parseSseBuffer } from './sse-parser.util';

export interface CoachingCue {
  message: string;
  timestamp: Date;
  triggerType: 'PERIODIC' | 'BLOCK_START' | 'BLOCK_END';
}

interface CoachingRequest {
  sportType: string;
  ftp: number;
  blockType: string;
  blockLabel: string;
  blockIndex: number;
  totalBlocks: number;
  targetIntensityPercent: number;
  remainingSeconds: number;
  blockDurationSeconds: number;
  avgPower: number;
  avgCadence: number;
  avgHeartRate: number;
  blockAvgPower: number;
  blockAvgCadence: number;
  blockAvgHR: number;
  sessionAvgPower: number;
  sessionAvgHR: number;
  triggerType: string;
}

const PERIODIC_INTERVAL_MS = 15_000;
const METRICS_WINDOW = 15;

@Injectable({ providedIn: 'root' })
export class LiveCoachingService {
  private bluetoothService = inject(BluetoothService);
  private executionService = inject(WorkoutExecutionService);
  private trainingService = inject(TrainingService);
  private ngZone = inject(NgZone);

  private cueSubject = new BehaviorSubject<CoachingCue | null>(null);
  cue$ = this.cueSubject.asObservable();

  private enabledSubject = new BehaviorSubject<boolean>(
    localStorage.getItem('ai-coaching-enabled') !== 'false',
  );
  enabled$ = this.enabledSubject.asObservable();

  private recentMetrics: LiveMetrics[] = [];
  private metricsSub: Subscription | null = null;
  private blockSub: Subscription | null = null;
  private periodicTimer: ReturnType<typeof setInterval> | null = null;
  private inFlight = false;
  private running = false;

  get enabled(): boolean {
    return this.enabledSubject.value;
  }

  toggle(): void {
    const next = !this.enabledSubject.value;
    this.enabledSubject.next(next);
    localStorage.setItem('ai-coaching-enabled', String(next));
    if (next && !this.running) {
      this.start();
    } else if (!next) {
      this.stop();
    }
  }

  start(): void {
    if (this.running) return;
    if (!this.enabledSubject.value) return;
    this.running = true;
    this.recentMetrics = [];

    // Collect metrics in a rolling window
    this.metricsSub = this.bluetoothService.metrics$.subscribe((m) => {
      this.recentMetrics.push(m);
      if (this.recentMetrics.length > METRICS_WINDOW) {
        this.recentMetrics.shift();
      }
    });

    // Detect block transitions
    this.blockSub = this.executionService.state$
      .pipe(
        map((s) => s.currentBlockIndex),
        distinctUntilChanged(),
      )
      .subscribe((blockIndex) => {
        if (blockIndex > 0) {
          this.requestCue('BLOCK_END');
        }
        if (blockIndex >= 0) {
          // Small delay so BLOCK_END fires first
          setTimeout(() => this.requestCue('BLOCK_START'), 500);
        }
      });

    // Periodic coaching every 15 seconds
    this.periodicTimer = setInterval(() => {
      const state = this.executionService.currentState;
      if (state.isActive && !state.isPaused && !this.inFlight) {
        this.requestCue('PERIODIC');
      }
    }, PERIODIC_INTERVAL_MS);
  }

  stop(): void {
    this.running = false;
    this.metricsSub?.unsubscribe();
    this.metricsSub = null;
    this.blockSub?.unsubscribe();
    this.blockSub = null;
    if (this.periodicTimer) {
      clearInterval(this.periodicTimer);
      this.periodicTimer = null;
    }
    this.recentMetrics = [];
    this.cueSubject.next(null);
  }

  private async requestCue(triggerType: string): Promise<void> {
    if (!this.enabledSubject.value || !this.running) return;

    const state = this.executionService.currentState;
    if (!state.training || !state.isActive) return;

    const block = state.flatBlocks[state.currentBlockIndex];
    if (!block) return;

    this.inFlight = true;

    const avg = this.aggregateMetrics();
    const ftp = this.trainingService.currentFtp ?? 200;

    const request: CoachingRequest = {
      sportType: state.training.sportType || 'CYCLING',
      ftp,
      blockType: block.type,
      blockLabel: block.label || block.type,
      blockIndex: state.currentBlockIndex,
      totalBlocks: state.flatBlocks.length,
      targetIntensityPercent: block.intensityTarget || 0,
      remainingSeconds: state.remainingBlockSeconds,
      blockDurationSeconds: block.durationSeconds || 0,
      avgPower: avg.power,
      avgCadence: avg.cadence,
      avgHeartRate: avg.heartRate,
      blockAvgPower: state.currentBlockAverages.power,
      blockAvgCadence: state.currentBlockAverages.cadence,
      blockAvgHR: state.currentBlockAverages.heartRate,
      sessionAvgPower: state.averages.power,
      sessionAvgHR: state.averages.heartRate,
      triggerType,
    };

    try {
      const message = await this.fetchCoachingCue(request);
      if (message && message.trim()) {
        this.ngZone.run(() => {
          this.cueSubject.next({
            message: message.trim(),
            timestamp: new Date(),
            triggerType: triggerType as CoachingCue['triggerType'],
          });
        });
      }
    } catch {
      // Silently ignore coaching errors — non-critical feature
    } finally {
      this.inFlight = false;
    }
  }

  private async fetchCoachingCue(request: CoachingRequest): Promise<string> {
    const jwt = localStorage.getItem('token');
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (jwt) headers['Authorization'] = `Bearer ${jwt}`;

    const response = await fetch(`${environment.apiUrl}/api/ai/coaching/cue`, {
      method: 'POST',
      headers,
      body: JSON.stringify(request),
    });

    if (!response.ok) return '';

    const reader = response.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let result = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const { events, remaining } = parseSseBuffer(buffer);
      buffer = remaining;

      for (const { eventType, data } of events) {
        if (eventType === 'coaching') {
          result += data;
        }
      }
    }

    return result;
  }

  private aggregateMetrics(): { power: number; cadence: number; heartRate: number } {
    if (this.recentMetrics.length === 0) {
      return { power: 0, cadence: 0, heartRate: 0 };
    }
    const len = this.recentMetrics.length;
    let power = 0;
    let cadence = 0;
    let heartRate = 0;
    for (const m of this.recentMetrics) {
      power += m.power;
      cadence += m.cadence;
      heartRate += m.heartRate || 0;
    }
    return {
      power: Math.round(power / len),
      cadence: Math.round(cadence / len),
      heartRate: Math.round(heartRate / len),
    };
  }
}
