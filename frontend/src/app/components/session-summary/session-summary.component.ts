import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SessionSummary, BlockSummary } from '../../services/workout-execution.service';

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
}
