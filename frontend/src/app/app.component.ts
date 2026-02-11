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
  template: `
    <div class="app-container">
      <main class="main-content">
        <app-top-bar></app-top-bar>
        <div class="page-content">
          <router-outlet></router-outlet>
        </div>
      </main>

      <!-- Global Overlays -->
      <app-device-manager *ngIf="showDeviceManager$ | async"></app-device-manager>

      <!-- Live Workout Overlay -->
      <app-live-dashboard *ngIf="(executionState$ | async)?.isActive || (executionState$ | async)?.finalSummary"></app-live-dashboard>
    </div>
  `,
  styles: [`
    .app-container {
      display: flex;
      height: 100vh;
      width: 100vw;
      overflow: hidden;
      background: var(--bg-color);
    }
    .main-content {
      flex: 1;
      height: 100vh;
      display: flex;
      flex-direction: column;
      position: relative;
    }
    .page-content {
      flex: 1;
      height: calc(100vh - 64px);
      overflow: hidden;
    }
    .empty-state {
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px;
    }
    .hero-content {
      padding: 60px;
      max-width: 800px;
      text-align: center;
      border-radius: 32px;
    }
    .hero-content h1 {
      font-size: 48px;
      margin-bottom: 24px;
      background: var(--accent-gradient);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .hero-content p {
      font-size: 18px;
      color: var(--text-muted);
      line-height: 1.6;
    }
  `]
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
