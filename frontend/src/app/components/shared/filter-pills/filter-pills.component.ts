import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';

export interface FilterPillOption {
  label: string;
  value: string | null;
  color?: string;
}

@Component({
  selector: 'app-filter-pills',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './filter-pills.component.html',
  styleUrl: './filter-pills.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterPillsComponent {
  @Input() options: FilterPillOption[] = [];
  @Input() selected: string | null = null;
  @Output() selectionChange = new EventEmitter<string | null>();

  onSelect(value: string | null): void {
    this.selectionChange.emit(value);
  }
}
