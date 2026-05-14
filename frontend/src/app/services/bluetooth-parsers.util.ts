import { LiveMetrics } from './bluetooth.service';

export function parseTrainerData(data: DataView, current: LiveMetrics): LiveMetrics {
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
    // intentionally no `offset += 2` here — power is the last field we read.
  }

  return {
    ...current,
    power: power || current.power,
    cadence: cadence || current.cadence,
    speed: speed || current.speed,
    timestamp: new Date(),
  };
}

export function parseHRData(data: DataView, current: LiveMetrics): LiveMetrics {
  const flags = data.getUint8(0);
  const hrValue = flags & 0x01 ? data.getUint16(1, true) : data.getUint8(1);

  return {
    ...current,
    heartRate: hrValue,
    timestamp: new Date(),
  };
}

export function parsePowerMeterData(data: DataView, current: LiveMetrics): LiveMetrics {
  const power = data.getInt16(2, true);
  return {
    ...current,
    power,
    timestamp: new Date(),
  };
}

export function parseCadenceData(data: DataView, current: LiveMetrics): LiveMetrics {
  const flags = data.getUint8(0);
  if (flags & 0x02) {
    const rpm = data.getUint16(3, true) / 10;
    return {
      ...current,
      cadence: Math.round(rpm),
      timestamp: new Date(),
    };
  }
  return current;
}
