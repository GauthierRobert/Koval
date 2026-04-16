import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../environments/environment';

export interface SkillSummary {
  filename: string;
  name: string;
  description: string;
}

@Injectable({providedIn: 'root'})
export class SkillsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/api/skills`;

  getSkills(): Observable<SkillSummary[]> {
    return this.http.get<SkillSummary[]>(this.apiUrl);
  }

  getDownloadUrl(skillName: string): string {
    return `${this.apiUrl}/${skillName}.zip`;
  }

  getZipUrl(): string {
    return `${this.apiUrl}/koval-skills.zip`;
  }
}
