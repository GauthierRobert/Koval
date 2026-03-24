import {ChangeDetectionStrategy, Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterOutlet} from '@angular/router';
import {WorkoutExecutionService} from './services/workout-execution.service';
import {LiveDashboardComponent} from './components/pages/live-session/live-dashboard.component';
import {TopBarComponent} from './components/layout/top-bar/top-bar.component';
import {TrainingService} from './services/training.service';
import {Training} from './models/training.model';
import {Observable} from 'rxjs';
import {filter} from 'rxjs/operators';
import {DeviceManagerComponent} from './components/shared/device-manager/device-manager.component';
import {SettingsComponent} from './components/layout/settings/settings.component';
import {BluetoothService} from './services/bluetooth.service';
import {AuthService} from './services/auth.service';
import {NotificationService} from './services/notification.service';
import {NotificationToastComponent} from './components/shared/notification-toast/notification-toast.component';
import {ErrorToastComponent} from './components/shared/error-toast/error-toast.component';
import {TranslateModule, TranslateService} from '@ngx-translate/core';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    LiveDashboardComponent,
    TopBarComponent,
    DeviceManagerComponent,
    SettingsComponent,
    NotificationToastComponent,
    ErrorToastComponent,
    TranslateModule
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  selectedTraining$: Observable<Training | null>;
  executionState$: Observable<any>;
  showDeviceManager$: Observable<boolean>;
  showSettings$: Observable<boolean>;

  constructor(
    private trainingService: TrainingService,
    private executionService: WorkoutExecutionService,
    private bluetoothService: BluetoothService,
    private authService: AuthService,
    private notificationService: NotificationService,
    private translate: TranslateService,
  ) {
    this.selectedTraining$ = this.trainingService.selectedTraining$;
    this.executionState$ = this.executionService.state$;
    this.showDeviceManager$ = this.bluetoothService.showDeviceManager$;
    this.showSettings$ = this.authService.showSettings$;

    const savedLang = localStorage.getItem('lang');
    const browserLang = navigator.language?.split('-')[0];
    const supportedLangs = ['en', 'fr'];
    const lang =
      (savedLang && supportedLangs.includes(savedLang) ? savedLang : null) ??
      (browserLang && supportedLangs.includes(browserLang) ? browserLang : null) ??
      'en';
    translate.use(lang);

    // Auto-register for push notifications when user logs in
    this.authService.user$
      .pipe(filter((u) => u !== null))
      .subscribe(() => {
        this.notificationService.requestPermissionAndRegisterToken();
      });
  }
}
