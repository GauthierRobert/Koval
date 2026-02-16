import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ZoneSystem, SportType, Zone } from './zone';

@Injectable({
    providedIn: 'root',
})
export class ZoneService {
    private apiUrl = 'http://localhost:8080/api/zones';
    private http = inject(HttpClient);

    /** GET /api/zones/coach — list all zone systems for the current coach (JWT auth) */
    getCoachZoneSystems(): Observable<ZoneSystem[]> {
        return this.http.get<ZoneSystem[]>(`${this.apiUrl}/coach`);
    }

    /** POST /api/zones/coach — create a new zone system (JWT auth) */
    createZoneSystem(zoneSystem: ZoneSystem): Observable<ZoneSystem> {
        return this.http.post<ZoneSystem>(`${this.apiUrl}/coach`, zoneSystem);
    }

    /** PUT /api/zones/coach/{id} — update a zone system (JWT auth) */
    updateZoneSystem(id: string, zoneSystem: ZoneSystem): Observable<ZoneSystem> {
        return this.http.put<ZoneSystem>(`${this.apiUrl}/coach/${id}`, zoneSystem);
    }

    /** DELETE /api/zones/coach/{id} — delete a zone system (JWT auth) */
    deleteZoneSystem(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/coach/${id}`);
    }

    /** PUT /api/zones/coach/{id}/active — set a zone system as active (JWT auth) */
    setActiveSystem(zoneSystemId: string): Observable<void> {
        return this.http.put<void>(`${this.apiUrl}/coach/${zoneSystemId}/active`, {});
    }

    /** GET /api/zones/athlete/effective?sportType=X — get effective zones for athlete (JWT auth) */
    getEffectiveZones(sportType: SportType): Observable<Zone[]> {
        return this.http.get<Zone[]>(`${this.apiUrl}/athlete/effective`, {
            params: { sportType }
        });
    }
}
