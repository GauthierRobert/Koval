import {Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {BluetoothService} from '../../../services/bluetooth.service';

@Component({
  selector: 'app-device-manager',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './device-manager.component.html',
  styleUrl: './device-manager.component.css'
})
export class DeviceManagerComponent {
  private bluetoothService = inject(BluetoothService);

  trainerStatus$ = this.bluetoothService.trainerStatus$;
  hrStatus$ = this.bluetoothService.hrStatus$;
  pmStatus$ = this.bluetoothService.pmStatus$;
  cadenceStatus$ = this.bluetoothService.cadenceStatus$;

  connectTrainer() {
    this.bluetoothService.connectTrainer();
  }

  connectHR() {
    this.bluetoothService.connectHeartRate();
  }

  connectPM() {
    this.bluetoothService.connectPowerMeter();
  }

  connectCadence() {
    this.bluetoothService.connectCadenceSensor();
  }

  onClose() {
    this.bluetoothService.toggleDeviceManager(false);
  }
}
