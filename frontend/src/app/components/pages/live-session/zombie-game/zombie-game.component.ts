import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  inject,
  Input,
  NgZone,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-zombie-game',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './zombie-game.component.html',
  styleUrl: './zombie-game.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ZombieGameComponent implements OnInit, OnDestroy {
  @Input() targetPower = 200;
  @Input() currentPower = 0;
  @Output() gameEnded = new EventEmitter<void>();

  private readonly ngZone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);

  bgPos = 0;
  playerX = 400;
  zombieX = 50;
  lives = 3;
  isTooFast = false;

  private animationId?: number;
  private lastUpdate = Date.now();

  ngOnInit() {
    // requestAnimationFrame fires every frame; running it inside the Angular zone
    // would trigger global change detection 60×/sec. Stay outside the zone and
    // markForCheck only on this component.
    this.ngZone.runOutsideAngular(() => this.gameLoop());
  }

  ngOnDestroy() {
    if (this.animationId) cancelAnimationFrame(this.animationId);
  }

  private gameLoop = () => {
    const now = Date.now();
    const dt = (now - this.lastUpdate) / 1000;
    this.lastUpdate = now;

    this.updateSystem(dt);
    this.cdr.markForCheck();
    this.animationId = requestAnimationFrame(this.gameLoop);
  };

  private updateSystem(dt: number) {
    if (this.lives <= 0) return;

    // Background scroll speed based on power
    const scrollSpeed = 200 * (this.currentPower / this.targetPower || 1);
    this.bgPos -= scrollSpeed * dt;

    // Zombie logic
    const powerRatio = this.currentPower / this.targetPower;
    this.isTooFast = powerRatio > 1.15;

    let zombieSpeed: number;
    if (powerRatio < 0.85) {
      zombieSpeed = 150; // Zombie sprints
    } else if (powerRatio < 0.95) {
      zombieSpeed = 80; // Zombie closes in
    } else {
      zombieSpeed = -50; // Zombie falls back
    }

    this.zombieX += zombieSpeed * dt;

    // Constraints
    this.zombieX = Math.max(-100, Math.min(this.playerX - 20, this.zombieX));

    // Collision check
    if (this.zombieX >= this.playerX - 60) {
      this.loseLife();
    }
  }

  private loseLife() {
    this.lives--;
    this.zombieX = -200;
  }

  getPowerColor(): string {
    const ratio = this.currentPower / this.targetPower;
    if (ratio < 0.85) return '#e74c3c';
    if (ratio < 0.95) return '#f1c40f';
    if (ratio < 1.15) return '#2ecc71';
    return '#e67e22';
  }

  closeGame() {
    this.ngZone.run(() => this.gameEnded.emit());
  }
}
