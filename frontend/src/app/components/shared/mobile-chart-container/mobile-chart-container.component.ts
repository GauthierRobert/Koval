import {CommonModule} from '@angular/common';
import {Component, ContentChild, Input, TemplateRef} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';

@Component({
    selector: 'app-mobile-chart-container',
    standalone: true,
    imports: [CommonModule, TranslateModule],
    templateUrl: './mobile-chart-container.component.html',
    styleUrls: ['./mobile-chart-container.component.css'],
})
export class MobileChartContainerComponent {
    @Input() title = '';
    @Input() hint: string | null = null;
    @Input() iconTpl: TemplateRef<unknown> | null = null;

    @ContentChild(TemplateRef) chartTpl!: TemplateRef<unknown>;

    open = false;
}
