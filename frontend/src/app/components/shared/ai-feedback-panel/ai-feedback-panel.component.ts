import {ChangeDetectionStrategy, Component, inject, Input, OnChanges, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, map, startWith, switchMap} from 'rxjs/operators';
import {AiAnalysisService} from '../../../services/ai-analysis.service';
import {AiAnalysis, Provenance} from '../../../models/ai-analysis.model';

interface FeedItem {
    id: string;
    summary: string;
    body: string;
    highlights?: string[] | null;
    provenance: Provenance;
    createdAt: string;
}

interface PanelState {
    items: FeedItem[];
    loading: boolean;
    error: boolean;
}

@Component({
    selector: 'app-ai-feedback-panel',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './ai-feedback-panel.component.html',
    styleUrl: './ai-feedback-panel.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AiFeedbackPanelComponent implements OnChanges {
    private analysisService = inject(AiAnalysisService);

    @Input({required: true}) sessionId!: string;

    private refresh$ = new BehaviorSubject<void>(undefined);

    state$: Observable<PanelState> = this.refresh$.pipe(
        switchMap(() => this.load()),
    );

    private load(): Observable<PanelState> {
        if (!this.sessionId) {
            return of({items: [], loading: false, error: false});
        }
        return this.analysisService.listForSession$(this.sessionId).pipe(
            map((analyses) => this.toItems(analyses)),
            map((items) => ({items, loading: false, error: false})),
            startWith<PanelState>({items: [], loading: true, error: false}),
            catchError(() => of<PanelState>({items: [], loading: false, error: true})),
        );
    }

    private toItems(analyses: AiAnalysis[]): FeedItem[] {
        return analyses.map((x) => ({
            id: x.id,
            summary: x.summary,
            body: x.body,
            highlights: x.highlights,
            provenance: x.provenance,
            createdAt: x.createdAt,
        }));
    }

    ngOnChanges(_changes: SimpleChanges): void {
        this.refresh$.next();
    }

    isMcpSourced(item: FeedItem): boolean {
        return item.provenance?.source === 'mcp';
    }

    trackById(_index: number, item: FeedItem): string {
        return item.id;
    }
}
