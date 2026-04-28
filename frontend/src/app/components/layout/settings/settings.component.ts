import {ChangeDetectionStrategy, Component, HostListener, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {AuthService} from '../../../services/auth.service';
import {ThemeService} from '../../../services/theme.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {A11yModule} from '@angular/cdk/a11y';

@Component({
    selector: 'app-settings',
    standalone: true,
    imports: [CommonModule, FormsModule, TranslateModule, A11yModule],
    templateUrl: './settings.component.html',
    styleUrls: ['./settings.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent {
    private authService = inject(AuthService);
    private translateService = inject(TranslateService);
    themeService = inject(ThemeService);

    currentLang = this.translateService.currentLang || 'en';

    setLang(lang: string): void {
        this.currentLang = lang;
        this.translateService.use(lang);
        localStorage.setItem('lang', lang);
    }

    close() {
        this.authService.toggleSettings(false);
    }

    @HostListener('document:keydown.escape')
    onEscapeKey(): void {
        this.close();
    }
}
