import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {Location} from '@angular/common';
import {Observable, of} from 'rxjs';
import {catchError, map, shareReplay, switchMap} from 'rxjs/operators';
import {
    BriefingCourseSummary,
    BriefingHourly,
    BriefingZoneRow,
    RaceBriefing,
    RaceService,
} from '../../../services/race.service';

/**
 * Race-day briefing page. One printable A4 surface that the athlete can save
 * as PDF (browser "Save as PDF") and either print physically or keep on phone.
 *
 * <p>Sections degrade gracefully: missing GPX hides the course section, races
 * outside the 16-day weather window show a placeholder card, no zone systems
 * yet → the zone section is skipped.
 */
@Component({
    selector: 'app-race-briefing',
    standalone: true,
    imports: [CommonModule, TranslateModule],
    templateUrl: './race-briefing.component.html',
    styleUrl: './race-briefing.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RaceBriefingComponent {
    private route = inject(ActivatedRoute);
    private raceService = inject(RaceService);
    private location = inject(Location);

    state$: Observable<{ briefing: RaceBriefing | null; error: boolean }> = this.route.paramMap.pipe(
        switchMap((p) => {
            const id = p.get('id');
            if (!id) return of({ briefing: null, error: true });
            return this.raceService.getBriefing(id).pipe(
                map((b) => ({ briefing: b, error: false })),
                catchError(() => of({ briefing: null, error: true })),
            );
        }),
        shareReplay({ bufferSize: 1, refCount: true }),
    );

    disciplineLabel(discipline: string): string {
        switch (discipline) {
            case 'swim': return 'Swim';
            case 'bike': return 'Bike';
            case 'run': return 'Run';
            default: return discipline;
        }
    }

    formatDistance(meters: number | null | undefined): string {
        if (meters == null) return '—';
        if (meters >= 1000) return `${(meters / 1000).toFixed(meters >= 10000 ? 1 : 2)} km`;
        return `${Math.round(meters)} m`;
    }

    formatElevation(m: number | null | undefined): string {
        if (m == null) return '—';
        return `${Math.round(m)} m`;
    }

    formatGradient(percent: number | null | undefined): string {
        if (percent == null) return '—';
        return `${percent.toFixed(1)}%`;
    }

    /** Hours kept in the hourly forecast strip — focus on race-start through afternoon. */
    raceHours(hourly: BriefingHourly[] | undefined): BriefingHourly[] {
        if (!hourly?.length) return [];
        // Open-Meteo returns 24 hourly slots. Race day is usually 06h → 18h.
        return hourly.filter((h) => {
            const hour = Number((h.time ?? '').slice(11, 13));
            return !Number.isNaN(hour) && hour >= 5 && hour <= 20;
        });
    }

    weatherIcon(code: number | undefined): string {
        if (code == null) return '·';
        // WMO weather codes → simple emoji map. Keeps the briefing scannable
        // without pulling in an icon library.
        if (code === 0) return '☀️';
        if (code <= 2) return '🌤️';
        if (code === 3) return '☁️';
        if (code >= 45 && code <= 48) return '🌫️';
        if (code >= 51 && code <= 67) return '🌧️';
        if (code >= 71 && code <= 77) return '❄️';
        if (code >= 80 && code <= 82) return '🌦️';
        if (code >= 85 && code <= 86) return '🌨️';
        if (code >= 95) return '⛈️';
        return '·';
    }

    zoneBarStyle(zone: BriefingZoneRow): string {
        const max = 200;
        const lo = Math.max(0, Math.min(zone.low, max));
        const hi = Math.max(lo, Math.min(zone.high, max));
        const left = (lo / max) * 100;
        const width = ((hi - lo) / max) * 100;
        return `left:${left}%;width:${width}%;`;
    }

    course(courses: BriefingCourseSummary[], discipline: string): BriefingCourseSummary | undefined {
        return courses.find((c) => c.discipline === discipline);
    }

    print(): void {
        window.print();
    }

    back(): void {
        this.location.back();
    }
}
