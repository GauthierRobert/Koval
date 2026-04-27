import {ChangeDetectionStrategy, Component, inject, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {BehaviorSubject, Observable, combineLatest, of} from 'rxjs';
import {catchError, distinctUntilChanged, map, startWith, switchMap} from 'rxjs/operators';
import {SavedSession} from '../../../../services/history.service';
import {AuthService, User} from '../../../../services/auth.service';
import {FitRecord, MetricsService} from '../../../../services/metrics.service';
import {ZoneClassificationService} from '../../../../services/zone-classification.service';
import {ZoneInterpolationService} from '../../../../services/zone-interpolation.service';
import {SportType, ZoneBlock} from '../../../../services/zone';
import {SportIconComponent} from '../../../shared/sport-icon/sport-icon.component';
import {FitTimeseriesChartComponent} from '../../session-analysis/fit-timeseries-chart/fit-timeseries-chart.component';
import {formatTrainingDuration} from '../../../shared/format/format.utils';

interface ChartData {
  records: FitRecord[];
  zoneBlocks: ZoneBlock[];
  sportType: SportType;
  ftp: number | null;
}

const SMOOTH_FACTOR = 10;

@Component({
  selector: 'app-last-activity-card',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent, FitTimeseriesChartComponent],
  templateUrl: './last-activity-card.component.html',
  styleUrl: './last-activity-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LastActivityCardComponent {
  private router = inject(Router);
  private metricsService = inject(MetricsService);
  private authService = inject(AuthService);
  private zoneCls = inject(ZoneClassificationService);
  private zoneInterp = inject(ZoneInterpolationService);

  private sessionSubject = new BehaviorSubject<SavedSession | null>(null);

  @Input() set session(value: SavedSession | null) {
    this.sessionSubject.next(value ?? null);
  }
  get session(): SavedSession | null {
    return this.sessionSubject.value;
  }

  session$: Observable<SavedSession | null> = this.sessionSubject.asObservable();

  private records$: Observable<FitRecord[]> = this.sessionSubject.pipe(
    distinctUntilChanged((a, b) => a?.fitFileId === b?.fitFileId && a?.id === b?.id),
    switchMap((s) => {
      if (!s?.fitFileId) return of([] as FitRecord[]);
      return this.metricsService.getCachedFitRecords(s.id).pipe(
        catchError(() => of([] as FitRecord[])),
        startWith([] as FitRecord[]),
      );
    }),
  );

  chart$: Observable<ChartData | null> = combineLatest([
    this.sessionSubject,
    this.records$,
    this.authService.user$,
  ]).pipe(
    map(([session, records, user]) => {
      if (!session || !records.length) return null;
      const sport = this.resolveSport(session.sportType);
      const ref = this.resolveReference(sport, user);
      const ftp = user?.ftp ?? null;
      if (!ref) return {records, zoneBlocks: [] as ZoneBlock[], sportType: sport, ftp};
      const zones = this.zoneCls.defaultZonesBySport[sport];
      const zoneBlocks = this.zoneInterp.computeZoneBlocks(records, zones, ref, sport, SMOOTH_FACTOR);
      return {records, zoneBlocks, sportType: sport, ftp};
    }),
  );

  formatDuration(s: number): string {
    return formatTrainingDuration(s);
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('en-US', {month: 'short', day: 'numeric'});
  }

  formatFitDistance(session: SavedSession): string {
    if (!session.avgSpeed || session.avgSpeed <= 0) return '—';
    const meters = session.avgSpeed * session.totalDuration;
    if (session.sportType === 'SWIMMING') return `${Math.round(meters)}m`;
    return `${(meters / 1000).toFixed(1)} km`;
  }

  openAnalysis(session: SavedSession): void {
    this.router.navigate(['/history', session.id]);
  }

  private resolveSport(sport: SavedSession['sportType']): SportType {
    return sport === 'RUNNING' || sport === 'SWIMMING' ? sport : 'CYCLING';
  }

  private resolveReference(sport: SportType, user: User | null): number | null {
    if (!user) return null;
    if (sport === 'CYCLING') return user.ftp ?? null;
    if (sport === 'RUNNING') {
      const ftpPace = user.functionalThresholdPace;
      return ftpPace ? 1000 / ftpPace : null;
    }
    const css = user.criticalSwimSpeed;
    return css ? 100 / css : null;
  }
}
