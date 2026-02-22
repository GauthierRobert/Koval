import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SessionSummary, BlockSummary } from '../../services/workout-execution.service';
import { AuthService } from '../../services/auth.service';
import { FitExportService } from '../../services/fit-export.service';

@Component({
    selector: 'app-session-summary',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './session-summary.component.html',
    styleUrl: './session-summary.component.css'
})
export class SessionSummaryComponent {
    @Input() summary!: SessionSummary;
    @Output() close = new EventEmitter<void>();
    private authService = inject(AuthService);
    private fitExport = inject(FitExportService);

    formatTime(seconds: number): string {
        const m = Math.floor(seconds / 60);
        const s = seconds % 60;
        return `${m}:${s.toString().padStart(2, '0')}`;
    }

    getDelta(block: BlockSummary): number {
        if (block.targetPower === 0) return 0;
        const diff = block.actualPower - block.targetPower;
        return Math.round((diff / block.targetPower) * 100);
    }

    getSportLabel(): string {
        if (!this.summary) return 'POWER';
        if (this.summary.sportType === 'RUNNING' || this.summary.sportType === 'SWIMMING') return 'PACE';
        return 'POWER';
    }

    getSportUnit(): string {
        if (!this.summary) return 'W';
        if (this.summary.sportType === 'RUNNING') return '/km';
        if (this.summary.sportType === 'SWIMMING') return '/100m';
        return 'W';
    }

    downloadFit(): void {
        this.fitExport.exportSession(this.summary);
    }

    formatIntensity(watts: number): string {
        if (!this.summary || this.summary.sportType === 'CYCLING') return watts + 'W';
        // For run/swim, watts here is actually raw power—show as-is with unit
        return watts + this.getSportUnit();
    }
}
