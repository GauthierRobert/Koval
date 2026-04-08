import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { PwaInstallService } from '../../../services/pwa-install.service';

@Component({
  selector: 'app-install-banner',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './install-banner.component.html',
  styleUrl: './install-banner.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstallBannerComponent {
  private pwaInstallService = inject(PwaInstallService);

  installable$ = this.pwaInstallService.installable$;

  install(): void {
    void this.pwaInstallService.prompt();
  }

  dismiss(): void {
    this.pwaInstallService.dismiss();
  }
}
