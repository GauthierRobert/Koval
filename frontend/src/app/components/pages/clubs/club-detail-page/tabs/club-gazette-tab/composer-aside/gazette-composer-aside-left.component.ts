import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {DatePipe} from '@angular/common';
import {ClubGazetteEditionResponse} from '../../../../../../../models/club-gazette.model';

/**
 * Left rail of the gazette composer: target edition card, tips,
 * and a list of my contributions to the current draft so far.
 */
@Component({
  selector: 'gazette-composer-aside-left',
  standalone: true,
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="gz2-back-rail" (click)="back.emit()">
      <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
      Back to editions
    </a>

    @if (draft(); as d) {
      <section class="gz2-card edition-card">
        <span class="gz2-lbl">▲ Target edition</span>
        <h4 class="gz2-edition-num">Edition #{{d.editionNumber}}</h4>
        <div class="gz2-edition-meta">
          <span class="status-dot"></span>
          <span class="status">Draft</span>
          <span class="dot-sep">·</span>
          <span>{{d.periodStart | date:'MMM d'}} → {{d.periodEnd | date:'MMM d'}}</span>
        </div>
        <div class="gz2-edition-counts">
          <div>
            <span class="gz2-count-lbl">Posts</span>
            <b>{{postsCount()}}</b>
          </div>
          <div>
            <span class="gz2-count-lbl">Photos</span>
            <b>{{photosCount()}}</b>
          </div>
          <div>
            <span class="gz2-count-lbl">Mine</span>
            <b>{{myPostsCount()}}</b>
          </div>
        </div>
      </section>
    }

    <section class="gz2-card tip-card">
      <h6>Gazette tips</h6>
      <ul>
        <li>A <b>reflection</b> = a feeling or a week summary.</li>
        <li>Linking a <b>session</b> lets readers duplicate it.</li>
        <li>Photos in <b>JPEG / PNG / WebP</b>, 8 MB max each.</li>
      </ul>
    </section>

    @if (myPostsCount() > 0) {
      <section class="gz2-card tip-card">
        <h6>Your contributions</h6>
        <div class="gz2-mine-count">{{myPostsCount()}} post{{myPostsCount() === 1 ? '' : 's'}} this week</div>
        <div class="gz2-mine-hint">Already in this draft. Add more or edit later.</div>
      </section>
    }
  `,
  styles: [`
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--space-sm);
      min-width: 0;
    }

    .gz2-back-rail {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-family: var(--font-display);
      font-size: var(--text-xs);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--text-dim);
      cursor: pointer;
      padding: 4px 0;
    }
    .gz2-back-rail:hover { color: var(--text-color); }

    .gz2-card {
      background: var(--glass-bg);
      border: 1px solid var(--glass-border);
      border-radius: var(--radius-md);
      padding: var(--space-md);
    }

    .edition-card {
      background: linear-gradient(135deg, var(--accent-subtle), var(--glass-bg) 60%);
      border-color: var(--accent-border);
    }

    .gz2-lbl {
      display: inline-block;
      font-family: var(--font-display);
      font-size: var(--text-xs);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--accent-color);
      font-weight: 700;
    }

    .gz2-edition-num {
      font-family: var(--font-display);
      font-size: var(--text-xl);
      font-weight: 700;
      letter-spacing: -0.01em;
      margin: 6px 0 4px;
    }

    .gz2-edition-meta {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: var(--text-xs);
      color: var(--text-muted);
    }
    .status { color: var(--accent-color); font-weight: 600; }
    .status-dot {
      width: 6px; height: 6px;
      border-radius: 50%;
      background: var(--accent-color);
      box-shadow: 0 0 6px var(--accent-color);
    }
    .dot-sep { color: var(--text-dim); }

    .gz2-edition-counts {
      display: flex;
      gap: var(--space-md);
      margin-top: var(--space-sm);
      padding-top: var(--space-sm);
      border-top: 1px dashed var(--glass-border);
    }
    .gz2-edition-counts div { display: flex; flex-direction: column; gap: 2px; }
    .gz2-count-lbl {
      font-size: 9px;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--text-dim);
    }
    .gz2-edition-counts b {
      font-family: var(--font-display);
      font-size: var(--text-base);
      font-weight: 700;
      color: var(--text-color);
      font-variant-numeric: tabular-nums;
    }

    .tip-card h6 {
      font-family: var(--font-display);
      font-size: var(--text-xs);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--text-dim);
      margin: 0 0 8px;
      font-weight: 700;
    }
    .tip-card ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .tip-card li {
      font-size: var(--text-sm);
      color: var(--text-muted);
      line-height: 1.5;
      padding-left: 12px;
      position: relative;
    }
    .tip-card li::before {
      content: '·';
      position: absolute;
      left: 0;
      color: var(--accent-color);
      font-weight: 700;
    }
    .tip-card b { color: var(--text-color); font-weight: 600; }

    .gz2-mine-count {
      font-size: var(--text-sm);
      color: var(--text-color);
      font-weight: 600;
    }
    .gz2-mine-hint {
      font-size: var(--text-xs);
      color: var(--text-dim);
      margin-top: 4px;
    }
  `],
})
export class GazetteComposerAsideLeftComponent {
  readonly draft = input<ClubGazetteEditionResponse | null>(null);
  readonly postsCount = input<number>(0);
  readonly photosCount = input<number>(0);
  readonly myPostsCount = input<number>(0);
  readonly back = output<void>();
}
