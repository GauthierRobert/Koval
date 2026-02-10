import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SessionSummary, BlockSummary } from '../../services/workout-execution.service';

@Component({
    selector: 'app-session-summary',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="summary-overlay">
      <div class="summary-card">
        <header class="summary-header">
          <h1>WORKOUT COMPLETE</h1>
          <p class="workout-title">{{ summary.title }}</p>
        </header>

        <div class="top-stats">
          <div class="stat-item">
            <span class="label">TOTAL TIME</span>
            <span class="value">{{ formatTime(summary.totalDuration) }}</span>
          </div>
          <div class="stat-item">
            <span class="label">AVG POWER</span>
            <span class="value">{{ summary.avgPower }}W</span>
          </div>
          <div class="stat-item">
            <span class="label">AVG HR</span>
            <span class="value hr">{{ summary.avgHR > 0 ? summary.avgHR : '--' }}</span>
          </div>
          <div class="stat-item">
            <span class="label">AVG CADENCE</span>
            <span class="value">{{ summary.avgCadence }} RPM</span>
          </div>
        </div>

        <div class="table-container">
          <table class="summary-table">
            <thead>
              <tr>
                <th>INTERVAL</th>
                <th>DURATION</th>
                <th>TARGET</th>
                <th>ACTUAL</th>
                <th>DELTA</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let block of summary.blockSummaries">
                <td>
                  <span class="block-type" [class]="block.type.toLowerCase()">{{ block.label }}</span>
                </td>
                <td>{{ formatTime(block.durationSeconds) }}</td>
                <td>{{ block.targetPower }}W</td>
                <td>{{ block.actualPower }}W</td>
                <td>
                  <span class="delta" [class.positive]="getDelta(block) >= 0" [class.negative]="getDelta(block) < 0">
                    {{ getDelta(block) > 0 ? '+' : '' }}{{ getDelta(block) }}%
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer class="summary-footer">
          <button class="done-btn" (click)="close.emit()">DONE</button>
        </footer>
      </div>
    </div>
  `,
    styles: [`
    .summary-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0,0,0,0.9);
      backdrop-filter: blur(10px);
      z-index: 3000;
      display: flex;
      align-items: center;
      justify-content: center;
      animation: fadeIn 0.4s ease-out;
    }

    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

    .summary-card {
      background: #1a1a1a;
      width: 90%;
      max-width: 900px;
      padding: 40px;
      border-radius: 24px;
      border: 1px solid rgba(255,255,255,0.1);
      box-shadow: 0 20px 50px rgba(0,0,0,0.5);
    }

    .summary-header { text-align: center; margin-bottom: 40px; }
    .summary-header h1 { font-size: 14px; color: var(--accent-color); letter-spacing: 4px; margin-bottom: 8px; font-weight: 800; }
    .workout-title { font-size: 32px; font-weight: 700; color: white; }

    .top-stats {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 20px;
      margin-bottom: 40px;
    }
    .stat-item {
      background: rgba(255,255,255,0.05);
      padding: 20px;
      border-radius: 16px;
      text-align: center;
    }
    .stat-item .label { display: block; font-size: 10px; color: rgba(255,255,255,0.5); font-weight: 700; text-transform: uppercase; margin-bottom: 8px; }
    .stat-item .value { font-size: 24px; font-weight: 800; color: white; }
    .stat-item .value.hr { color: #e74c3c; }

    .table-container {
      max-height: 400px;
      overflow-y: auto;
      margin-bottom: 40px;
      border-radius: 12px;
      background: rgba(255,255,255,0.02);
    }

    .summary-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
    }
    .summary-table th {
      padding: 15px 20px;
      font-size: 11px;
      color: rgba(255,255,255,0.4);
      text-transform: uppercase;
      font-weight: 700;
      border-bottom: 1px solid rgba(255,255,255,0.05);
    }
    .summary-table td {
      padding: 15px 20px;
      font-size: 15px;
      color: white;
      border-bottom: 1px solid rgba(255,255,255,0.03);
    }

    .block-type {
      display: inline-block;
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 11px;
      font-weight: 700;
      background: rgba(255,255,255,0.1);
      text-transform: uppercase;
    }
    .block-type.warmup { background: rgba(52, 152, 219, 0.2); color: #3498db; }
    .block-type.interval { background: rgba(231, 76, 60, 0.2); color: #e74c3c; }
    .block-type.cooldown { background: rgba(46, 204, 113, 0.2); color: #2ecc71; }
    .block-type.steady { background: rgba(155, 89, 182, 0.2); color: #9b59b6; }

    .delta { font-weight: 700; }
    .delta.positive { color: #2ecc71; }
    .delta.negative { color: #e74c3c; }

    .summary-footer { text-align: center; }
    .done-btn {
      background: var(--accent-color);
      color: white;
      border: none;
      padding: 15px 60px;
      font-size: 18px;
      font-weight: 800;
      border-radius: 40px;
      cursor: pointer;
      transition: 0.2s;
      box-shadow: 0 10px 20px rgba(52, 152, 219, 0.3);
    }
    .done-btn:hover { transform: translateY(-2px); filter: brightness(1.2); }
  `]
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
