import { Injectable, EventEmitter } from '@angular/core';

@Injectable({
    providedIn: 'root'
})
export class PipService {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private video: HTMLVideoElement;

    // Events for dashboard to listen to
    public onPlay = new EventEmitter<void>();
    public onPause = new EventEmitter<void>();
    public onStop = new EventEmitter<void>();

    constructor() {
        this.canvas = document.createElement('canvas');
        this.canvas.width = 800;  // 2x scale for 400px logical width
        this.canvas.height = 280; // Increased from 240 to 280 for more spacing
        this.ctx = this.canvas.getContext('2d')!;

        this.video = document.createElement('video');
        this.video.muted = true;
        this.video.playsInline = true;
    }

    async togglePip(data: { power: number, target: number, hr: number, time: string, color: string, blockLabel: string, nextStepLabel: string, nextStepPower: number, totalTime: string, isPaused: boolean }) {
        if (document.pictureInPictureElement) {
            await document.exitPictureInPicture();
        } else {
            this.updateCanvas(data);
            const stream = (this.canvas as any).captureStream(10); // 10 FPS is enough for metrics
            this.video.srcObject = stream;

            // Always play to ensure PiP opens successfully
            await this.video.play();

            await this.video.requestPictureInPicture();

            // Sync media session state to reflect actual workout state
            this.setupMediaSession(data.isPaused);

            // If strictly paused, we could pause the video here, but keeping it "playing" 
            // ensures the canvas updates are reflected if the UI repaints. 
            // The MediaSession state handles the OS controls.
        }
    }

    private setupMediaSession(initialIsPaused: boolean) {
        if ('mediaSession' in navigator) {
            navigator.mediaSession.metadata = new MediaMetadata({
                title: 'Workout Tracker',
                artist: 'AI Training Planner',
                album: 'Live Session'
            });

            navigator.mediaSession.playbackState = initialIsPaused ? 'paused' : 'playing';

            navigator.mediaSession.setActionHandler('play', async () => {
                this.onPlay.emit();
                try {
                    await this.video.play();
                    navigator.mediaSession.playbackState = 'playing';
                } catch (e) { console.error('PiP Play Error', e); }
            });
            navigator.mediaSession.setActionHandler('pause', () => {
                this.onPause.emit();
                this.video.pause();
                navigator.mediaSession.playbackState = 'paused';
            });
            navigator.mediaSession.setActionHandler('stop', () => {
                this.onStop.emit();
                document.exitPictureInPicture();
            });
        }
    }

    public async updatePlaybackState(isPaused: boolean) {
        if ('mediaSession' in navigator) {
            navigator.mediaSession.playbackState = isPaused ? 'paused' : 'playing';
            try {
                if (isPaused) this.video.pause();
                else await this.video.play();
            } catch (e) { /* ignore play errors when hidden */ }
        }
    }

    updateCanvas(data: { power: number, target: number, hr: number, time: string, color: string, blockLabel: string, nextStepLabel: string, nextStepPower: number, totalTime: string, isPaused: boolean }) {
        const ctx = this.ctx;
        const w = this.canvas.width;
        const h = this.canvas.height;

        // Background
        ctx.fillStyle = '#111';
        ctx.fillRect(0, 0, w, h);

        // Border
        ctx.strokeStyle = '#333';
        ctx.lineWidth = 4;
        ctx.strokeRect(2, 2, w - 4, h - 4);

        // Scaling for high-DPI crispness (using 2x units for drawing)
        const s = 2;

        // Upper Left Zone: NOW label
        ctx.fillStyle = '#aaa';
        ctx.font = `bold ${11 * s}px Inter, sans-serif`;
        ctx.textAlign = 'left';
        ctx.fillText(`NOW: ${data.blockLabel.toUpperCase()}`, 20 * s, 25 * s);

        // Bottom Left Zone: NEXT label (Moved lower to avoid metrics)
        ctx.fillStyle = '#666';
        ctx.font = `bold ${10 * s}px Inter, sans-serif`;
        const nextText = data.nextStepPower > 0 ? `NEXT: ${data.nextStepLabel.toUpperCase()} (${data.nextStepPower}W)` : `NEXT: ${data.nextStepLabel.toUpperCase()}`;
        ctx.fillText(nextText, 20 * s, 125 * s); // Moved from 110 to 125

        // Upper Right Zone: Overall Time
        ctx.textAlign = 'right';
        ctx.fillStyle = '#666';
        ctx.font = `bold ${11 * s}px Inter, sans-serif`;
        ctx.fillText(`TOTAL: ${data.totalTime}`, 380 * s, 25 * s);

        // Center-Left Zone: MAIN POWER
        ctx.fillStyle = data.color;
        ctx.font = `bold ${64 * s}px Inter, sans-serif`;
        ctx.textAlign = 'center';
        ctx.fillText(data.power.toString(), 110 * s, 85 * s);

        // Center-Left Sub: Target Watts
        const powerWidth = ctx.measureText(data.power.toString()).width;
        ctx.font = `bold ${20 * s}px Inter, sans-serif`;
        ctx.fillStyle = '#666';
        ctx.textAlign = 'left';
        ctx.fillText(`/${data.target}W`, (110 * s) + (powerWidth / 2) + (5 * s), 80 * s);

        ctx.font = `bold ${10 * s}px Inter, sans-serif`;
        ctx.fillStyle = '#999';
        ctx.textAlign = 'center';
        ctx.fillText('POWER', 110 * s, 102 * s); // Moved up slightly from 105

        // RIGHT AREA: HR & INTERVAL TIMER
        // We stack these vertically to avoid overlapping the central power area
        ctx.textAlign = 'center';

        // HR
        ctx.fillStyle = '#e74c3c';
        ctx.font = `bold ${32 * s}px Inter, sans-serif`;
        ctx.fillText(data.hr > 0 ? data.hr.toString() : '--', 260 * s, 65 * s);
        ctx.font = `bold ${9 * s}px Inter, sans-serif`;
        ctx.fillStyle = '#666';
        ctx.fillText('HR', 260 * s, 82 * s);

        // Interval Remaining
        ctx.fillStyle = '#3498db';
        ctx.font = `bold ${32 * s}px Inter, sans-serif`;
        ctx.fillText(data.time, 350 * s, 65 * s);
        ctx.font = `bold ${9 * s}px Inter, sans-serif`;
        ctx.fillStyle = '#666';
        ctx.fillText('INTERVAL', 350 * s, 82 * s);
    }

    get isPipActive(): boolean {
        return !!document.pictureInPictureElement;
    }
}
