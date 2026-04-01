import {PmcDataPoint} from './metrics.service';

export function computeIF(avgPower: number, ftp: number): number {
    if (!ftp || ftp <= 0 || !avgPower) return 0;
    return avgPower / ftp;
}

export function computeTss(durationSeconds: number, avgPower: number, ftp: number): number {
    if (!ftp || ftp <= 0 || !avgPower || !durationSeconds) return 0;
    const intensityFactor = computeIF(avgPower, ftp);
    return (durationSeconds / 3600) * intensityFactor * intensityFactor * 100;
}

export function computeTssFromRpe(durationSeconds: number, rpe: number): number {
    if (!durationSeconds || !rpe) return 0;
    const intensity = rpe / 10;
    return (durationSeconds / 3600) * intensity * intensity * 100;
}

export function projectPmc(realData: PmcDataPoint[], dailyTss: number, days: number): PmcDataPoint[] {
    if (!realData.length) return [];
    const last = realData[realData.length - 1];
    const kCTL = 1 - Math.exp(-1 / 42);
    const kATL = 1 - Math.exp(-1 / 7);

    let ctl = last.ctl;
    let atl = last.atl;
    const lastDate = new Date(last.date);
    const result: PmcDataPoint[] = [];

    for (let i = 1; i <= days; i++) {
        ctl = ctl + (dailyTss - ctl) * kCTL;
        atl = atl + (dailyTss - atl) * kATL;
        const d = new Date(lastDate);
        d.setDate(d.getDate() + i);
        result.push({
            date: d.toISOString().split('T')[0],
            ctl: Math.round(ctl * 10) / 10,
            atl: Math.round(atl * 10) / 10,
            tsb: Math.round((ctl - atl) * 10) / 10,
            dailyTss,
            predicted: true,
        });
    }
    return result;
}

export function findPeakForm(data: PmcDataPoint[]): { date: string; tsb: number } | null {
    if (!data.length) return null;
    return data.reduce((best, d) => (d.tsb > best.tsb ? d : best), data[0]);
}

export function projectPmcFromSchedule(
    realData: PmcDataPoint[],
    scheduledTss: Map<string, number>,
    days: number,
): PmcDataPoint[] {
    if (!realData.length) return [];
    const last = realData[realData.length - 1];
    const kCTL = 1 - Math.exp(-1 / 42);
    const kATL = 1 - Math.exp(-1 / 7);
    let ctl = last.ctl;
    let atl = last.atl;
    const lastDate = new Date(last.date);
    const result: PmcDataPoint[] = [];

    for (let i = 1; i <= days; i++) {
        const d = new Date(lastDate);
        d.setDate(d.getDate() + i);
        const dateStr = d.toISOString().split('T')[0];
        const dayTss = scheduledTss.get(dateStr) ?? 0;
        ctl = ctl + (dayTss - ctl) * kCTL;
        atl = atl + (dayTss - atl) * kATL;
        result.push({
            date: dateStr,
            ctl: Math.round(ctl * 10) / 10,
            atl: Math.round(atl * 10) / 10,
            tsb: Math.round((ctl - atl) * 10) / 10,
            dailyTss: dayTss,
            predicted: true,
        });
    }
    return result;
}
