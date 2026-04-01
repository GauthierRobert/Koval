import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-discipline-selector',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './discipline-selector.component.html',
  styleUrl: './discipline-selector.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisciplineSelectorComponent {
  @Input() discipline = 'TRIATHLON';
  @Input() bikeType = 'ROAD_AERO';
  @Input() showBikeType = false;

  @Output() disciplineChange = new EventEmitter<string>();
  @Output() bikeTypeChange = new EventEmitter<string>();

  setDiscipline(value: string): void {
    this.disciplineChange.emit(value);
  }

  setBikeType(value: string): void {
    this.bikeTypeChange.emit(value);
  }
}
