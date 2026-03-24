import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {AuthService} from '../../../services/auth.service';
import {BehaviorSubject} from 'rxjs';

@Component({
  selector: 'app-cgu-modal',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './cgu-modal.component.html',
  styleUrl: './cgu-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CguModalComponent {
  private authService = inject(AuthService);
  user$ = this.authService.user$;
  private acceptingSubject = new BehaviorSubject(false);
  accepting$ = this.acceptingSubject.asObservable();

  accept(): void {
    this.acceptingSubject.next(true);
    this.authService.acceptCgu().subscribe({
      next: () => this.acceptingSubject.next(false),
      error: () => this.acceptingSubject.next(false),
    });
  }
}
