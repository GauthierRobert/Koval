import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-gpx-upload-zone',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './gpx-upload-zone.component.html',
  styleUrl: './gpx-upload-zone.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GpxUploadZoneComponent {
  @Input() fileName: string | null = null;
  @Input() label = '';
  @Input() loops = 1;
  @Input() showLoops = true;
  @Input() hint = '';

  @Output() fileSelected = new EventEmitter<File>();
  @Output() loopsChange = new EventEmitter<number>();

  isDragging = false;

  get hasFile(): boolean {
    return !!this.fileName;
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = true;
  }

  onDragLeave(): void {
    this.isDragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;
    if (event.dataTransfer?.files.length) {
      this.fileSelected.emit(event.dataTransfer.files[0]);
    }
  }

  onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.fileSelected.emit(input.files[0]);
    }
  }

  onLoopsChange(value: number): void {
    this.loopsChange.emit(value);
  }
}
