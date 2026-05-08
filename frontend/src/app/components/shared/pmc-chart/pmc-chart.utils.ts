const SPORT_COLORS: Record<string, string> = {
    SWIMMING: '#00a0e9',
    CYCLING: '#34d399',
    RUNNING: '#f87171',
    BRICK: '#A78BFA',
    GYM: '#F472B6',
};

export function getSportColor(sport?: string): string {
    return SPORT_COLORS[sport || ''] || '#34d399';
}

export function dateToDays(date: string): number {
    return Math.round(new Date(date + 'T12:00:00').getTime() / 86400000);
}

export function daysToDate(days: number): string {
    return new Date(days * 86400000 + 43200000).toISOString().split('T')[0];
}

export function addDaysToDate(date: string, n: number): string {
    return daysToDate(dateToDays(date) + n);
}

export function roundRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number,
    r: number,
): void {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.arcTo(x + w, y, x + w, y + r, r);
    ctx.lineTo(x + w, y + h - r);
    ctx.arcTo(x + w, y + h, x + w - r, y + h, r);
    ctx.lineTo(x + r, y + h);
    ctx.arcTo(x, y + h, x, y + h - r, r);
    ctx.lineTo(x, y + r);
    ctx.arcTo(x, y, x + r, y, r);
    ctx.closePath();
}
