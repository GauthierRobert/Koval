import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { WorkoutExecutionService } from './services/workout-execution.service';
import { LiveDashboardComponent } from './components/live-dashboard/live-dashboard.component';
import { TopBarComponent } from './components/top-bar/top-bar.component';
import { TrainingService, Training } from './services/training.service';
import { Observable } from 'rxjs';
import { DeviceManagerComponent } from './components/device-manager/device-manager.component';
import { SettingsComponent } from './components/settings/settings.component';
import { BluetoothService } from './services/bluetooth.service';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    LiveDashboardComponent,
    TopBarComponent,
    DeviceManagerComponent,
    SettingsComponent
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
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
    private authService: AuthService
  ) {
    this.selectedTraining$ = this.trainingService.selectedTraining$;
    this.executionState$ = this.executionService.state$;
    this.showDeviceManager$ = this.bluetoothService.showDeviceManager$;
    this.showSettings$ = this.authService.showSettings$;
  }
}
