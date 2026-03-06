import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, Observable } from 'rxjs';
import { TagService, Tag } from '../../services/tag.service';
import { CoachService } from '../../services/coach.service';
import { User } from '../../services/auth.service';

@Component({
  selector: 'app-tag-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tag-management.component.html',
  styleUrl: './tag-management.component.css',
})
export class TagManagementComponent implements OnInit {
  private tagsSubject = new BehaviorSubject<Tag[]>([]);
  tags$: Observable<Tag[]> = this.tagsSubject.asObservable();

  tagAthletes = new Map<string, User[]>();
  editingTagId: string | null = null;
  editingName = '';
  newTagName = '';
  newTagMaxAthletes = 0;

  constructor(private tagService: TagService, private coachService: CoachService) {}

  ngOnInit(): void {
    this.loadTags();
  }

  private loadTags(): void {
    this.tagService.getTags().subscribe({
      next: (tags) => {
        this.tagsSubject.next(tags);
        this.loadAthletesForTags(tags);
      },
      error: () => this.tagsSubject.next([]),
    });
  }

  private loadAthletesForTags(tags: Tag[]): void {
    this.coachService.getAthletes().subscribe({
      next: (athletes) => {
        for (const tag of tags) {
          this.tagAthletes.set(
            tag.id,
            athletes.filter((a) => a.tags?.includes(tag.name))
          );
        }
      },
      error: () => {},
    });
  }

  getAthletes(tagId: string): User[] {
    return this.tagAthletes.get(tagId) ?? [];
  }

  startEdit(tag: Tag): void {
    this.editingTagId = tag.id;
    this.editingName = tag.name;
  }

  saveEdit(tag: Tag): void {
    if (!this.editingName.trim() || this.editingName.trim() === tag.name) {
      this.editingTagId = null;
      return;
    }
    this.tagService.renameTag(tag.id, this.editingName.trim()).subscribe({
      next: () => {
        this.editingTagId = null;
        this.loadTags();
      },
      error: () => { this.editingTagId = null; },
    });
  }

  cancelEdit(): void {
    this.editingTagId = null;
  }

  removeAthleteFromTag(athlete: User, tag: Tag): void {
    this.coachService.removeAthleteTag(athlete.id, tag.name).subscribe({
      next: () => this.loadTags(),
      error: () => {},
    });
  }

  createTag(): void {
    if (!this.newTagName.trim()) return;
    this.tagService.createTag(this.newTagName.trim(), this.newTagMaxAthletes).subscribe({
      next: () => {
        this.newTagName = '';
        this.newTagMaxAthletes = 0;
        this.loadTags();
      },
      error: () => {},
    });
  }

  deleteTag(tag: Tag): void {
    if (!confirm(`Delete tag "${tag.name}"? This will remove it from all athletes.`)) return;
    this.tagService.deleteTag(tag.id).subscribe({
      next: () => this.loadTags(),
      error: () => {},
    });
  }
}
