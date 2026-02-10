import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, interval, map } from 'rxjs';

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

    private trainerDevice: any = null;
    private heartRateDevice: any = null;
    private powerMeterDevice: any = null;
    private cadenceDevice: any = null;

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
            characteristic.addEventListener('characteristicvaluechanged', (event: any) => {
                this.handleTrainerData(event.target.value);
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
            characteristic.addEventListener('characteristicvaluechanged', (event: any) => {
                this.handleHRData(event.target.value);
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
            characteristic.addEventListener('characteristicvaluechanged', (event: any) => {
                this.handlePowerMeterData(event.target.value);
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
            characteristic.addEventListener('characteristicvaluechanged', (event: any) => {
                this.handleCadenceData(event.target.value);
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
        const flags = data.getUint16(0, true);
        let offset = 2;

        let speed = 0;
        if (!(flags & 0x01)) {
            speed = data.getUint16(offset, true) / 100;
            offset += 2;
        }

        let cadence = 0;
        if (flags & 0x04) {
            cadence = data.getUint16(offset, true) * 0.5;
            offset += 2;
        }

        let power = 0;
        if (flags & 0x40) {
            power = data.getInt16(offset, true);
            offset += 2;
        }

        const current = this.metricsSubject.value;
        this.metricsSubject.next({
            ...current,
            power: power || current.power,
            cadence: cadence || current.cadence,
            speed: speed || current.speed,
            timestamp: new Date()
        });
    }

    private handleHRData(data: DataView) {
        const flags = data.getUint8(0);
        const hrValue = (flags & 0x01) ? data.getUint16(1, true) : data.getUint8(1);

        const current = this.metricsSubject.value;
        this.metricsSubject.next({
            ...current,
            heartRate: hrValue,
            timestamp: new Date()
        });
    }

    private handlePowerMeterData(data: DataView) {
        // Cycling Power Measurement (0x2A63)
        const power = data.getInt16(2, true);
        const current = this.metricsSubject.value;
        this.metricsSubject.next({
            ...current,
            power: power,
            timestamp: new Date()
        });
    }

    private handleCadenceData(data: DataView) {
        // CSC Measurement (0x2A5B)
        const flags = data.getUint8(0);
        if (flags & 0x02) { // Crank Revolution Data Present
            // Simple RPM fallback logic if high-res delta calculation not implemented
            const rpm = data.getUint16(3, true) / 10;
            const current = this.metricsSubject.value;
            this.metricsSubject.next({
                ...current,
                cadence: Math.round(rpm),
                timestamp: new Date()
            });
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

    toggleSimulation(active: boolean) {
        this.isSimulating = active;
        if (active) {
            this.isConnectedSubject.next(true);
            this.startSimulation();
        } else {
            this.checkOverallConnection();
        }
    }

    private startSimulation() {
        interval(1000).subscribe(() => {
            if (!this.isSimulating) return;

            const basePower = 200;
            const baseCadence = 85;
            const baseSpeed = 30;

            const current = this.metricsSubject.value;
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
        this.trainerStatusSubject.next('Disconnected');
        this.hrStatusSubject.next('Disconnected');
        this.pmStatusSubject.next('Disconnected');
        this.cadenceStatusSubject.next('Disconnected');
        this.isConnectedSubject.next(false);
    }
}
