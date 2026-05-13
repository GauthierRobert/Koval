/**
 * TypeScript interfaces for Web Bluetooth API
 * Self-contained types (no dependency on global Web Bluetooth type definitions)
 */

export interface BluetoothRemoteGATTCharacteristic {
  uuid: string;
  value?: DataView;
  startNotifications(): Promise<BluetoothRemoteGATTCharacteristic>;
  addEventListener(type: 'characteristicvaluechanged', listener: (event: Event) => void): void;
}

export interface BluetoothRemoteGATTService {
  getCharacteristic(uuid: string): Promise<BluetoothRemoteGATTCharacteristic>;
}

export interface BluetoothRemoteGATTServer {
  connect(): Promise<BluetoothRemoteGATTServer>;
  disconnect(): void;
  connected: boolean;
  getPrimaryService(uuid: string): Promise<BluetoothRemoteGATTService>;
}

export interface BluetoothDevice {
  id: string;
  name?: string;
  gatt: BluetoothRemoteGATTServer;
  addEventListener(type: string, listener: (event: Event) => void): void;
}

export interface BluetoothRequestDeviceOptions {
  filters?: ReadonlyArray<{ services?: string[]; name?: string; namePrefix?: string }>;
  optionalServices?: string[];
  acceptAllDevices?: boolean;
}

export interface BluetoothPlugin {
  requestDevice(options: BluetoothRequestDeviceOptions): Promise<BluetoothDevice>;
}

/** Web Bluetooth lives off `navigator.bluetooth`; not in the standard DOM lib. */
export type NavigatorWithBluetooth = Navigator & { bluetooth: BluetoothPlugin };

export interface BluetoothCharacteristic {
  uuid: string;
  value?: DataView;
  properties: Record<string, boolean>;
}

export interface CharacteristicValueChangedEvent {
  target: { value: DataView };
}

export interface TrainerData {
  power: number;
  resistance: number;
  speed: number;
  cadence: number;
}

export interface HeartRateData {
  heartRate: number;
  timestamp: number;
}

export interface PowerMeterData {
  power: number;
  cadence: number;
  balance?: number;
}

export interface CadenceSensorData {
  cadence: number;
  wheelRevolutions?: number;
  crankRevolutions?: number;
}

export interface BluetoothDevicePool {
  trainerDevice: BluetoothDevice | null;
  heartRateDevice: BluetoothDevice | null;
  powerMeterDevice: BluetoothDevice | null;
  cadenceDevice: BluetoothDevice | null;
}

export interface BluetoothCharacteristicPool {
  trainerCharacteristic: BluetoothCharacteristic | null;
  heartRateCharacteristic: BluetoothCharacteristic | null;
  powerCharacteristic: BluetoothCharacteristic | null;
  cadenceCharacteristic: BluetoothCharacteristic | null;
}
