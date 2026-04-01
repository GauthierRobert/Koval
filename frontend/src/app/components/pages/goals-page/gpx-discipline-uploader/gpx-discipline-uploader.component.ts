import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {Race, RouteCoordinate} from '../../../../services/race.service';
import {RouteMapComponent} from '../../pacing/route-map/route-map.component';

@Component({
  selector: 'app-gpx-discipline-uploader',
  standalone: true,
  imports: [CommonModule, TranslateModule, RouteMapComponent],
  templateUrl: './gpx-discipline-uploader.component.html',
  styleUrl: './gpx-discipline-uploader.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GpxDisciplineUploaderComponent {
  @Input() race!: Race;
  @Input() discipline!: string;
  @Input() hasGpx = false;
  @Input() isUploading = false;
  @Input() routeCoords: RouteCoordinate[] | null = null;

  @Output() upload = new EventEmitter<{ file: File; discipline: string }>();
  @Output() reupload = new EventEmitter<{ file: File; discipline: string }>();

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      if (this.hasGpx) {
        this.reupload.emit({ file, discipline: this.discipline });
      } else {
        this.upload.emit({ file, discipline: this.discipline });
      }
    }
  }
}
