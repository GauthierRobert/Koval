import {Injectable} from '@angular/core';
import {BehaviorSubject, interval, Subscription} from 'rxjs';
import {BluetoothDevice, CharacteristicValueChangedEvent} from '../models/bluetooth-types.model';
import {parseCadenceData, parseHRData, parsePowerMeterData, parseTrainerData} from './bluetooth-parsers.util';

export interface LiveMetrics {
    power: number;
    cadence: number;
    speed: number;
    heartRate?: number;
    timestamp: Date;
}

@Injectable({
    providedIn: 'root'
})
export class BluetoothService {
    private metricsSubject = new BehaviorSubject<LiveMetrics>({
        power: 0,
        cadence: 0,
        speed: 0,
        timestamp: new Date()
    });
    metrics$ = this.metricsSubject.asObservable();

    private isConnectedSubject = new BehaviorSubject<boolean>(false);
    isConnected$ = this.isConnectedSubject.asObservable();

    private trainerDevice: BluetoothDevice | null = null;
    private heartRateDevice: BluetoothDevice | null = null;
    private powerMeterDevice: BluetoothDevice | null = null;
    private cadenceDevice: BluetoothDevice | null = null;

    private trainerStatusSubject = new BehaviorSubject<string>('Disconnected');
    trainerStatus$ = this.trainerStatusSubject.asObservable();

    private hrStatusSubject = new BehaviorSubject<string>('Disconnected');
    hrStatus$ = this.hrStatusSubject.asObservable();

    private pmStatusSubject = new BehaviorSubject<string>('Disconnected');
    pmStatus$ = this.pmStatusSubject.asObservable();

    private cadenceStatusSubject = new BehaviorSubject<string>('Disconnected');
    cadenceStatus$ = this.cadenceStatusSubject.asObservable();

    private showDeviceManagerSubject = new BehaviorSubject<boolean>(false);
    showDeviceManager$ = this.showDeviceManagerSubject.asObservable();
    private isSimulating = false;
    private simulationSubscription: Subscription | null = null;

    constructor() { }

    toggleDeviceManager(show?: boolean) {
        if (show !== undefined) {
            this.showDeviceManagerSubject.next(show);
        } else {
            this.showDeviceManagerSubject.next(!this.showDeviceManagerSubject.value);
        }
    }

    async connectTrainer() {
        try {
            this.trainerStatusSubject.next('Scanning...');
            const device = await (navigator as any).bluetooth.requestDevice({
                filters: [{ services: ['fitness_machine'] }]
            });

            this.trainerDevice = device;
            this.trainerStatusSubject.next('Connecting...');

            const server = await device.gatt.connect();
            const service = await server.getPrimaryService('fitness_machine');
            const characteristic = await service.getCharacteristic('indoor_bike_data');

            await characteristic.startNotifications();
            characteristic.addEventListener('characteristicvaluechanged', (event: Event) => {
                const changeEvent = event as unknown as CharacteristicValueChangedEvent;
                this.handleTrainerData(changeEvent.target.value);
            });

            this.trainerStatusSubject.next('Connected');
            this.isConnectedSubject.next(true);

            device.addEventListener('gattserverdisconnected', () => {
                this.trainerStatusSubject.next('Disconnected');
                this.checkOverallConnection();
            });

        } catch (error) {
            console.error('Trainer Connection Error:', error);
            this.trainerStatusSubject.next('Error');
        }
    }

    async connectHeartRate() {
        try {
            this.hrStatusSubject.next('Scanning...');
            const device = await (navigator as any).bluetooth.requestDevice({
                filters: [{ services: ['heart_rate'] }]
            });

            this.heartRateDevice = device;
            this.hrStatusSubject.next('Connecting...');

            const server = await device.gatt.connect();
            const service = await server.getPrimaryService('heart_rate');
            const characteristic = await service.getCharacteristic('heart_rate_measurement');

            await characteristic.startNotifications();
            characteristic.addEventListener('characteristicvaluechanged', (event: Event) => {
                const changeEvent = event as unknown as CharacteristicValueChangedEvent;
                this.handleHRData(changeEvent.target.value);
            });

            this.hrStatusSubject.next('Connected');
            this.isConnectedSubject.next(true);

            device.addEventListener('gattserverdisconnected', () => {
                this.hrStatusSubject.next('Disconnected');
                this.checkOverallConnection();
            });

        } catch (error) {
            console.error('HR Connection Error:', error);
            this.hrStatusSubject.next('Error');
        }
    }

    async connectPowerMeter() {
        try {
            this.pmStatusSubject.next('Scanning...');
            const device = await (navigator as any).bluetooth.requestDevice({
                filters: [{ services: ['cycling_power'] }]
            });

            this.powerMeterDevice = device;
            this.pmStatusSubject.next('Connecting...');

            const server = await device.gatt.connect();
            const service = await server.getPrimaryService('cycling_power');
            const characteristic = await service.getCharacteristic('cycling_power_measurement');

            await characteristic.startNotifications();
            characteristic.addEventListener('characteristicvaluechanged', (event: Event) => {
                const changeEvent = event as unknown as CharacteristicValueChangedEvent;
                this.handlePowerMeterData(changeEvent.target.value);
            });

            this.pmStatusSubject.next('Connected');
            this.isConnectedSubject.next(true);

            device.addEventListener('gattserverdisconnected', () => {
                this.pmStatusSubject.next('Disconnected');
                this.checkOverallConnection();
            });
        } catch (error) {
            console.error('PM Connection Error:', error);
            this.pmStatusSubject.next('Error');
        }
    }

    async connectCadenceSensor() {
        try {
            this.cadenceStatusSubject.next('Scanning...');
            const device = await (navigator as any).bluetooth.requestDevice({
                filters: [{ services: ['cycling_speed_and_cadence'] }]
            });

            this.cadenceDevice = device;
            this.cadenceStatusSubject.next('Connecting...');

            const server = await device.gatt.connect();
            const service = await server.getPrimaryService('cycling_speed_and_cadence');
            const characteristic = await service.getCharacteristic('csc_measurement');

            await characteristic.startNotifications();
            characteristic.addEventListener('characteristicvaluechanged', (event: Event) => {
                const changeEvent = event as unknown as CharacteristicValueChangedEvent;
                this.handleCadenceData(changeEvent.target.value);
            });

            this.cadenceStatusSubject.next('Connected');
            this.isConnectedSubject.next(true);

            device.addEventListener('gattserverdisconnected', () => {
                this.cadenceStatusSubject.next('Disconnected');
                this.checkOverallConnection();
            });
        } catch (error) {
            console.error('Cadence Connection Error:', error);
            this.cadenceStatusSubject.next('Error');
        }
    }

    private handleTrainerData(data: DataView) {
        this.metricsSubject.next(parseTrainerData(data, this.metricsSubject.value));
    }

    private handleHRData(data: DataView) {
        this.metricsSubject.next(parseHRData(data, this.metricsSubject.value));
    }

    private handlePowerMeterData(data: DataView) {
        this.metricsSubject.next(parsePowerMeterData(data, this.metricsSubject.value));
    }

    private handleCadenceData(data: DataView) {
        const updated = parseCadenceData(data, this.metricsSubject.value);
        if (updated !== this.metricsSubject.value) {
            this.metricsSubject.next(updated);
        }
    }

    private checkOverallConnection() {
        const anyConnected = this.trainerStatusSubject.value === 'Connected' ||
            this.hrStatusSubject.value === 'Connected' ||
            this.pmStatusSubject.value === 'Connected' ||
            this.cadenceStatusSubject.value === 'Connected' ||
            this.isSimulating;
        this.isConnectedSubject.next(anyConnected);
    }

    get currentMetrics(): LiveMetrics {
        return this.metricsSubject.value;
    }

    toggleSimulation(active: boolean) {
        this.isSimulating = active;
        if (active) {
            this.isConnectedSubject.next(true);
            this.startSimulation();
        } else {
            this.simulationSubscription?.unsubscribe();
            this.simulationSubscription = null;
            this.checkOverallConnection();
        }
    }

    private startSimulation() {
        this.simulationSubscription?.unsubscribe();
        this.simulationSubscription = interval(1000).subscribe(() => {
            if (!this.isSimulating) return;

            const basePower = 200;
            const baseCadence = 85;
            const baseSpeed = 30;

            this.metricsSubject.next({
                power: basePower + Math.floor(Math.random() * 20 - 10),
                cadence: baseCadence + Math.floor(Math.random() * 6 - 3),
                speed: baseSpeed + Math.random() * 2 - 1,
                heartRate: 140 + Math.floor(Math.random() * 10 - 5),
                timestamp: new Date()
            });
        });
    }

    disconnect() {
        if (this.trainerDevice) this.trainerDevice.gatt.disconnect();
        if (this.heartRateDevice) this.heartRateDevice.gatt.disconnect();
        if (this.powerMeterDevice) this.powerMeterDevice.gatt.disconnect();
        if (this.cadenceDevice) this.cadenceDevice.gatt.disconnect();
        this.isSimulating = false;
        this.simulationSubscription?.unsubscribe();
        this.simulationSubscription = null;
        this.trainerStatusSubject.next('Disconnected');
        this.hrStatusSubject.next('Disconnected');
        this.pmStatusSubject.next('Disconnected');
        this.cadenceStatusSubject.next('Disconnected');
        this.isConnectedSubject.next(false);
    }
}
