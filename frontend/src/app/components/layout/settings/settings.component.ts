import { ChangeDetectionStrategy, Component, HostListener, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { ThemePack, ThemeService, THEME_PACKS } from '../../../services/theme.service';
import { ContextService } from '../../../services/context.service';
import {
  ATHLETE_CONTEXT_SECTIONS,
  COACH_PHILOSOPHY_SECTIONS,
  ContextSections,
} from '../../../models/context.model';
import { ContextEditorComponent } from '../../shared/context-editor/context-editor.component';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { A11yModule } from '@angular/cdk/a11y';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, A11yModule, ContextEditorComponent],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent {
  private authService = inject(AuthService);
  private translateService = inject(TranslateService);
  private contextService = inject(ContextService);
  themeService = inject(ThemeService);

  currentLang = this.translateService.currentLang || 'en';
  readonly themePacks = THEME_PACKS;

  readonly athleteSections = ATHLETE_CONTEXT_SECTIONS;
  readonly coachSections = COACH_PHILOSOPHY_SECTIONS;

  myContext$ = this.contextService.myContext$;

  private savingSubject = new BehaviorSubject<boolean>(false);
  saving$ = this.savingSubject.asObservable();

  onSaveContext(sections: ContextSections): void {
    this.savingSubject.next(true);
    this.contextService.saveMyContext(sections).subscribe({
      next: () => this.savingSubject.next(false),
      error: () => this.savingSubject.next(false),
    });
  }

  setPack(pack: ThemePack): void {
    this.themeService.setPack(pack);
  }

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
