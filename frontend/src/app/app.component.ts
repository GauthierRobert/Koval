import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { WorkoutExecutionService } from './services/workout-execution.service';
import { LiveDashboardComponent } from './components/live-dashboard/live-dashboard.component';
import { TopBarComponent } from './components/top-bar/top-bar.component';
import { TrainingService, Training } from './services/training.service';
import { Observable } from 'rxjs';
import { DeviceManagerComponent } from './components/device-manager/device-manager.component';
import { BluetoothService } from './services/bluetooth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    LiveDashboardComponent,
    TopBarComponent,
    DeviceManagerComponent
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  selectedTraining$: Observable<Training | null>;
  executionState$: Observable<any>;
  showDeviceManager$: Observable<boolean>;

  constructor(
    private trainingService: TrainingService,
    private executionService: WorkoutExecutionService,
    private bluetoothService: BluetoothService
  ) {
    this.selectedTraining$ = this.trainingService.selectedTraining$;
    this.executionState$ = this.executionService.state$;
    this.showDeviceManager$ = this.bluetoothService.showDeviceManager$;
  }
}
