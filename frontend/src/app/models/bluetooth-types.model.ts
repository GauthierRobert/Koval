/**
 * TypeScript interfaces for Web Bluetooth API
 * Replaces scattered 'any' types with proper type definitions
 */

export interface BluetoothDevice extends globalThis.BluetoothDevice {
  id: string;
  name?: string;
  connected: boolean;
}

export interface BluetoothCharacteristic extends globalThis.BluetoothRemoteGATTCharacteristic {
  uuid: string;
  value?: DataView;
  properties: BluetoothCharacteristicProperties;
}

export interface CharacteristicValueChangedEvent extends Event {
  target: BluetoothCharacteristic;
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
