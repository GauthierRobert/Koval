import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, interval, Subscription} from 'rxjs';
import {Training, WorkoutBlock} from './training.service';
import {BluetoothService, LiveMetrics} from './bluetooth.service';

export type { LiveMetrics } from './bluetooth.service';

export interface BlockSummary {
    label: string;
    durationSeconds: number;
    targetPower: number;
    actualPower: number;
    actualCadence: number;
    actualHR: number;
    type: string;
}

export interface SessionSummary {
    title: string;
    totalDuration: number;
    avgPower: number;
    avgHR: number;
    avgCadence: number;
    avgSpeed: number; // m/s
    blockSummaries: BlockSummary[];
    history: LiveMetrics[];
    sportType: 'CYCLING' | 'RUNNING' | 'SWIMMING' | 'BRICK';
    scheduledWorkoutId?: string;
}

export interface ActiveSessionState {
    training: Training | null;
    currentBlockIndex: number;
    remainingBlockSeconds: number;
    elapsedTotalSeconds: number;
    isActive: boolean;
    isPaused: boolean;
    flatBlocks: WorkoutBlock[];
    history: LiveMetrics[];
    blockSummaries: BlockSummary[];
    finalSummary: SessionSummary | null;
    scheduledWorkoutId: string | null;
    averages: {
        power: number;
        cadence: number;
        speed: number;
        heartRate: number;
    };
    currentBlockAverages: {
        power: number;
        cadence: number;
        heartRate: number;
        speed: number;
    };
}

@Injectable({
    providedIn: 'root'
})
export class WorkoutExecutionService {
    private bluetoothService = inject(BluetoothService);

    private stateSubject = new BehaviorSubject<ActiveSessionState>({
        training: null,
        currentBlockIndex: 0,
        remainingBlockSeconds: 0,
        elapsedTotalSeconds: 0,
        isActive: false,
        isPaused: false,
        flatBlocks: [],
        history: [],
        blockSummaries: [],
        finalSummary: null,
        scheduledWorkoutId: null,
        averages: { power: 0, cadence: 0, speed: 0, heartRate: 0 },
        currentBlockAverages: { power: 0, cadence: 0, heartRate: 0, speed: 0 }
    });

    state$ = this.stateSubject.asObservable();
    private timerSubscription?: Subscription;
    private metricsSubscription?: Subscription;

    private flattenElements(blocks: WorkoutBlock[]): WorkoutBlock[] {
        // Backend now handles flattening. Pass through.
        return blocks || [];
    }

    startWorkout(training: Training, scheduledWorkoutId: string | null = null) {
        const flatBlocks = this.flattenElements(training.blocks || []);
        if (flatBlocks.length === 0) return;

        const initialDuration = flatBlocks[0].durationSeconds || 0;

        this.stateSubject.next({
            training,
            currentBlockIndex: 0,
            remainingBlockSeconds: initialDuration,
            elapsedTotalSeconds: 0,
            isActive: true,
            isPaused: true,
            flatBlocks,
            history: [],
            blockSummaries: [],
            finalSummary: null,
            scheduledWorkoutId,
            averages: { power: 0, cadence: 0, speed: 0, heartRate: 0 },
            currentBlockAverages: { power: 0, cadence: 0, heartRate: 0, speed: 0 }
        });

        // Do NOT startTimer yet, wait for resume/start click
        this.subscribeToMetrics();
    }

    togglePause() {
        const state = this.stateSubject.value;
        if (state.isPaused) this.resumeWorkout();
        else this.pauseWorkout();
    }

    pauseWorkout() {
        const currentState = this.stateSubject.value;
        this.stateSubject.next({ ...currentState, isPaused: true });
        this.stopTimer();
    }

    resumeWorkout() {
        const currentState = this.stateSubject.value;
        this.stateSubject.next({ ...currentState, isPaused: false });
        this.startTimer();
    }

    stopWorkout() {
        this.stopTimer();
        this.metricsSubscription?.unsubscribe();
        const state = this.stateSubject.value;

        if (state.isActive && state.training) {
            // Archive the current partial block
            this.archiveCurrentBlock();
            const finalState = this.stateSubject.value; // Get updated state with last block archived

            const summary: SessionSummary = {
                title: state.training.title,
                totalDuration: finalState.elapsedTotalSeconds,
                avgPower: finalState.averages.power,
                avgHR: finalState.averages.heartRate,
                avgCadence: finalState.averages.cadence,
                avgSpeed: finalState.averages.speed,
                blockSummaries: finalState.blockSummaries,
                history: finalState.history,
                sportType: state.training.sportType,
                scheduledWorkoutId: state.scheduledWorkoutId || undefined
            };

            this.stateSubject.next({ ...finalState, isActive: false, isPaused: false, finalSummary: summary });
        } else {
            this.stateSubject.next({ ...state, isActive: false, isPaused: false });
        }
    }

    private archiveCurrentBlock() {
        const state = this.stateSubject.value;
        if (!state.training) return;

        const block = state.flatBlocks[state.currentBlockIndex];
        const ftp = 250; // Mock FTP or get from training service if needed. Using 250 as placeholder like in TrainingService

        // Calculate target power for archiving (simple version)
        let target = 0;
        if (block.type === 'RAMP') {
            target = Math.round(((block.intensityStart || 0) + (block.intensityEnd || 0)) / 2 * ftp / 100);
        } else {
            target = Math.round((block.intensityTarget || 0) * ftp / 100);
        }

        const duration = block.durationSeconds || 0;
        const summary: BlockSummary = {
            label: block.label,
            durationSeconds: duration - state.remainingBlockSeconds, // Actual time spent
            targetPower: target,
            actualPower: state.currentBlockAverages.power,
            actualCadence: state.currentBlockAverages.cadence,
            actualHR: state.currentBlockAverages.heartRate,
            type: block.type
        };

        this.stateSubject.next({
            ...state,
            blockSummaries: [...state.blockSummaries, summary]
        });
    }

    skipBlock() {
        const state = this.stateSubject.value;
        if (!state.isActive || !state.training) return;

        const nextIndex = state.currentBlockIndex + 1;
        if (nextIndex < state.flatBlocks.length) {
            this.stateSubject.next({
                ...state,
                currentBlockIndex: nextIndex,
                remainingBlockSeconds: state.flatBlocks[nextIndex].durationSeconds || 0,
                currentBlockAverages: { power: 0, cadence: 0, heartRate: 0, speed: 0 }
            });
        } else {
            this.stopWorkout();
        }
    }

    private startTimer() {
        this.timerSubscription = interval(1000).subscribe(() => {
            this.tick();
        });
    }

    private stopTimer() {
        this.timerSubscription?.unsubscribe();
    }

    private tick() {
        const state = this.stateSubject.value;
        if (!state.training || state.isPaused) return;

        let remaining = state.remainingBlockSeconds - 1;
        let index = state.currentBlockIndex;
        let elapsed = state.elapsedTotalSeconds + 1;

        if (remaining <= 0) {
            this.archiveCurrentBlock();
            const updatedState = this.stateSubject.value; // Get state with archived block

            index++;
            if (index < state.flatBlocks.length) {
                remaining = state.flatBlocks[index].durationSeconds || 0;
                this.stateSubject.next({
                    ...updatedState,
                    currentBlockIndex: index,
                    remainingBlockSeconds: remaining,
                    elapsedTotalSeconds: elapsed,
                    currentBlockAverages: { power: 0, cadence: 0, heartRate: 0, speed: 0 }
                });
            } else {
                this.stopWorkout();
                return;
            }
        } else {
            this.stateSubject.next({
                ...state,
                currentBlockIndex: index,
                remainingBlockSeconds: remaining,
                elapsedTotalSeconds: elapsed
            });
        }
    }

    private subscribeToMetrics() {
        this.metricsSubscription = this.bluetoothService.metrics$.subscribe(metrics => {
            const state = this.stateSubject.value;
            if (!state.isActive || state.isPaused) return;

            const newHistory = [...state.history, metrics];

            // Calculate averages
            const totalPower = newHistory.reduce((acc, m) => acc + m.power, 0);
            const totalCadence = newHistory.reduce((acc, m) => acc + m.cadence, 0);
            const totalSpeed = newHistory.reduce((acc, m) => acc + m.speed, 0);
            const hrSamples = newHistory.filter(m => m.heartRate !== undefined);
            const totalHR = hrSamples.reduce((acc, m) => acc + m.heartRate!, 0);
            const count = newHistory.length;

            // Current block metrics mapping
            const blockDuration = state.flatBlocks[state.currentBlockIndex].durationSeconds || 0;
            const samplesInBlock = Math.max(1, blockDuration - state.remainingBlockSeconds);
            const blockHistory = newHistory.slice(-samplesInBlock);
            const blockPower = Math.round(blockHistory.reduce((acc, m) => acc + m.power, 0) / blockHistory.length);
            const blockCadence = Math.round(blockHistory.reduce((acc, m) => acc + m.cadence, 0) / blockHistory.length);
            const blockSpeed = Number((blockHistory.reduce((acc, m) => acc + m.speed, 0) / blockHistory.length).toFixed(1));
            const blockHRSamples = blockHistory.filter(m => m.heartRate !== undefined);
            const blockHR = blockHRSamples.length > 0 ? Math.round(blockHRSamples.reduce((acc, m) => acc + m.heartRate!, 0) / blockHRSamples.length) : 0;

            this.stateSubject.next({
                ...state,
                history: newHistory,
                averages: {
                    power: Math.round(totalPower / count),
                    cadence: Math.round(totalCadence / count),
                    speed: Number((totalSpeed / count).toFixed(1)),
                    heartRate: hrSamples.length > 0 ? Math.round(totalHR / hrSamples.length) : 0
                },
                currentBlockAverages: {
                    power: blockPower,
                    cadence: blockCadence,
                    heartRate: blockHR,
                    speed: blockSpeed
                }
            });
        });
    }
}
