import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { AuthService, User } from '../../services/auth.service';

@Component({
  selector: 'app-physiology-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './physiology-page.component.html',
  styleUrl: './physiology-page.component.css',
})
export class PhysiologyPageComponent {
  user$: Observable<User | null>;

  constructor(private authService: AuthService) {
    this.user$ = this.authService.user$;
  }

  getPowerZones(ftp: number | undefined): { name: string; low: number; high: number | null; color: string }[] {
    if (!ftp) return [];
    return [
      { name: 'Z1 — Active Recovery', low: 0,                          high: Math.round(ftp * 0.55),       color: '#60a5fa' },
      { name: 'Z2 — Endurance',       low: Math.round(ftp * 0.55) + 1, high: Math.round(ftp * 0.75),       color: '#34d399' },
      { name: 'Z3 — Tempo',           low: Math.round(ftp * 0.75) + 1, high: Math.round(ftp * 0.90),       color: '#fbbf24' },
      { name: 'Z4 — Threshold',       low: Math.round(ftp * 0.90) + 1, high: Math.round(ftp * 1.05),       color: '#f97316' },
      { name: 'Z5 — VO2Max',          low: Math.round(ftp * 1.05) + 1, high: Math.round(ftp * 1.20),       color: '#f43f5e' },
      { name: 'Z6 — Anaerobic',       low: Math.round(ftp * 1.20) + 1, high: null,                         color: '#a855f7' },
    ];
  }

  getEstimatedVo2Max(ftp: number | undefined): string {
    if (!ftp) return '—';
    return ((ftp / 0.757) * (10.8 / 70)).toFixed(1);
  }

  getSportDistribution(sessions: any[]): { sport: string; count: number; pct: number }[] {
    const map = new Map<string, number>();
    for (const s of sessions) map.set(s.sportType, (map.get(s.sportType) ?? 0) + 1);
    return Array.from(map.entries())
      .map(([sport, count]) => ({ sport, count, pct: Math.round((count / sessions.length) * 100) }))
      .sort((a, b) => b.count - a.count);
  }
}
