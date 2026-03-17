/**
 * TypeScript interfaces for Web Bluetooth API
 * Self-contained types (no dependency on global Web Bluetooth type definitions)
 */

export interface BluetoothDevice {
  id: string;
  name?: string;
  gatt: {
    connect(): Promise<any>;
    disconnect(): void;
    connected: boolean;
  };
  addEventListener(type: string, listener: (event: Event) => void): void;
}

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
