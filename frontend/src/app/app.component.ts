import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { WorkoutExecutionService } from './services/workout-execution.service';
import { LiveDashboardComponent } from './components/pages/live-session/live-dashboard.component';
import { TopBarComponent } from './components/layout/top-bar/top-bar.component';
import { TrainingService, Training } from './services/training.service';
import { Observable } from 'rxjs';
import { DeviceManagerComponent } from './components/shared/device-manager/device-manager.component';
import { SettingsComponent } from './components/layout/settings/settings.component';
import { BluetoothService } from './services/bluetooth.service';
import { AuthService } from './services/auth.service';
import { NotificationToastComponent } from './components/shared/notification-toast/notification-toast.component';

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
    NotificationToastComponent
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
  ) {
    this.selectedTraining$ = this.trainingService.selectedTraining$;
    this.executionState$ = this.executionService.state$;
    this.showDeviceManager$ = this.bluetoothService.showDeviceManager$;
    this.showSettings$ = this.authService.showSettings$;
  }
}
