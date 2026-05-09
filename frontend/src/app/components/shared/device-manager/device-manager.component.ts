import {ChangeDetectionStrategy, Component, HostListener, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {BluetoothService} from '../../../services/bluetooth.service';
import {A11yModule} from '@angular/cdk/a11y';

@Component({
  selector: 'app-device-manager',
  standalone: true,
  imports: [CommonModule, TranslateModule, A11yModule],
  templateUrl: './device-manager.component.html',
  styleUrl: './device-manager.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
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

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.onClose();
  }
}
