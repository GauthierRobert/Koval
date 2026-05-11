import {BlockSummary, SessionSummary} from '../../../services/workout-execution.service';
// @ts-ignore — fit-file-parser has no type declarations
import FitParser from 'fit-file-parser';

const SPORT_MAP: Record<string, SessionSummary['sportType']> = {
  cycling: 'CYCLING',
  running: 'RUNNING',
  swimming: 'SWIMMING',
};

export function parseFitToSession(fileName: string, buffer: ArrayBuffer): Promise<SessionSummary> {
  return new Promise((resolve, reject) => {
    const parser = new FitParser({force: true, mode: 'list'});

    parser.parse(buffer, (error: any, data: any) => {
      if (error) {
        reject(new Error(error));
        return;
      }

      const session = data.sessions?.[0];
      if (!session) {
        reject(new Error('No session found in FIT file'));
        return;
      }

      const blockSummaries: BlockSummary[] = (data.laps || []).map((lap: any, i: number) => ({
        label: `Lap ${i + 1}`,
        durationSeconds: Math.round(lap.total_elapsed_time || 0),
        targetPower: 0,
        actualPower: Math.round(lap.avg_power || 0),
        actualCadence: Math.round(lap.avg_cadence || 0),
        actualHR: Math.round(lap.avg_heart_rate || 0),
        type: 'STEADY',
      }));

      const name = fileName.replace(/\.fit$/i, '').replace(/[_-]+/g, ' ').trim();

      resolve({
        title: name || 'Uploaded Session',
        totalDuration: Math.round(session.total_elapsed_time || session.total_timer_time || 0),
        avgPower: Math.round(session.avg_power || 0),
        avgHR: Math.round(session.avg_heart_rate || 0),
        avgCadence: Math.round(session.avg_cadence || 0),
        avgSpeed: session.avg_speed || 0,
        sportType: SPORT_MAP[session.sport?.toLowerCase()] ?? 'CYCLING',
        blockSummaries,
        history: [],
      });
    });
  });
}
