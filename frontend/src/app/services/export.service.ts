import {Injectable} from '@angular/core';
import {Training, WorkoutBlock} from './training.service';

@Injectable({
    providedIn: 'root'
})
export class ExportService {

    exportToZwift(training: Training, ftp: number = 250): void {
        if (training.sportType !== 'CYCLING') {
            console.error('Zwift export is only available for cycling workouts.');
            return;
        }
        const xml = this.generateZwiftXML(training, ftp);
        this.downloadFile(xml, `${this.sanitizeFilename(training.title)}.zwo`, 'application/xml');
    }

    exportToJSON(training: Training): void {
        const json = JSON.stringify(training, null, 2);
        this.downloadFile(json, `${this.sanitizeFilename(training.title)}.json`, 'application/json');
    }

    private generateZwiftXML(training: Training, ftp: number): string {
        const workoutContent = this.blocksToZwiftXML(training.blocks || [], ftp);

        return `<?xml version="1.0" encoding="UTF-8"?>
<workout_file>
    <author>AI Training Planner</author>
    <name>${this.escapeXML(training.title)}</name>
    <description>${this.escapeXML(training.description)}</description>
    <sportType>bike</sportType>
    <tags></tags>
    <workout>
        ${workoutContent}
    </workout>
</workout_file>`;
    }

    private blocksToZwiftXML(blocks: WorkoutBlock[], ftp: number): string {
        if (!blocks) return '';
        return blocks.map(b => this.blockToZwiftXML(b, ftp))
            .filter(xml => xml !== '')
            .join('\n        ');
    }

    private blockToZwiftXML(block: WorkoutBlock, ftp: number): string {
        const duration = block.durationSeconds || 0;
        if (duration === 0 && block.type !== 'PAUSE') return ''; // Avoid zero-duration blocks in Zwift

        switch (block.type) {
            case 'PAUSE':
                return `<SteadyState Duration="${duration}" Power="0" pace="0">
            <textevent timeoffset="0" message="PAUSE: ${this.escapeXML(block.label)}"/>
        </SteadyState>`;
            case 'WARMUP':
                const warmupPower = (block.intensityTarget || 50) / 100;
                return `<Warmup Duration="${duration}" PowerLow="0.5" PowerHigh="${warmupPower.toFixed(2)}" pace="0">
            <textevent timeoffset="10" message="${this.escapeXML(block.label)}"/>
        </Warmup>`;

            case 'COOLDOWN':
                const cooldownPower = (block.intensityTarget || 50) / 100;
                return `<Cooldown Duration="${duration}" PowerLow="${cooldownPower.toFixed(2)}" PowerHigh="0.5" pace="0">
            <textevent timeoffset="10" message="${this.escapeXML(block.label)}"/>
        </Cooldown>`;

            case 'RAMP':
                const rampStart = (block.intensityStart || 50) / 100;
                const rampEnd = (block.intensityEnd || 100) / 100;
                return `<Ramp Duration="${duration}" PowerLow="${rampStart.toFixed(2)}" PowerHigh="${rampEnd.toFixed(2)}" pace="0">
            <textevent timeoffset="10" message="${this.escapeXML(block.label)}"/>
        </Ramp>`;

            case 'FREE':
                return `<FreeRide Duration="${duration}" Cadence="85">
            <textevent timeoffset="10" message="${this.escapeXML(block.label)} - Ride by feel"/>
        </FreeRide>`;

            case 'STEADY':
            case 'INTERVAL':
            default:
                const power = (block.intensityTarget || 75) / 100;
                const cadence = block.cadenceTarget || 90;
                return `<SteadyState Duration="${duration}" Power="${power.toFixed(2)}" pace="0" Cadence="${cadence}">
            <textevent timeoffset="10" message="${this.escapeXML(block.label)}"/>
        </SteadyState>`;
        }
    }

    private escapeXML(str: string): string {
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&apos;');
    }

    private sanitizeFilename(filename: string): string {
        return filename
            .replace(/[^a-z0-9]/gi, '_')
            .toLowerCase();
    }

    private downloadFile(content: string, filename: string, mimeType: string): void {
        const blob = new Blob([content], { type: mimeType });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
    }
}
