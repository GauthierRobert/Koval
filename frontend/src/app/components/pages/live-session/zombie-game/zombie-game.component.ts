import {Component, EventEmitter, Input, OnDestroy, OnInit, Output} from '@angular/core';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-zombie-game',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './zombie-game.component.html',
  styleUrl: './zombie-game.component.css'
})
export class ZombieGameComponent implements OnInit, OnDestroy {
  @Input() targetPower: number = 200;
  @Input() currentPower: number = 0;
  @Output() gameEnded = new EventEmitter<void>();

  bgPos = 0;
  playerX = 400;
  zombieX = 50;
  lives = 3;
  isTooFast = false;

  private animationId?: number;
  private lastUpdate = Date.now();

  ngOnInit() {
    this.gameLoop();
  }

  ngOnDestroy() {
    if (this.animationId) cancelAnimationFrame(this.animationId);
  }

  private gameLoop = () => {
    const now = Date.now();
    const dt = (now - this.lastUpdate) / 1000;
    this.lastUpdate = now;

    this.updateSystem(dt);
    this.animationId = requestAnimationFrame(this.gameLoop);
  }

  private updateSystem(dt: number) {
    if (this.lives <= 0) return;

    // Background scroll speed based on power
    const scrollSpeed = 200 * (this.currentPower / this.targetPower || 1);
    this.bgPos -= scrollSpeed * dt;

    // Zombie logic
    const powerRatio = this.currentPower / this.targetPower;
    this.isTooFast = powerRatio > 1.15;

    let zombieSpeed = 0;
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
    this.zombieX = -200; // Reset zombie distance
    if (this.lives === 0) {
      // Pause for a bit?
    }
  }

  getPowerColor(): string {
    const ratio = this.currentPower / this.targetPower;
    if (ratio < 0.85) return '#e74c3c'; // Red
    if (ratio < 0.95) return '#f1c40f'; // Yellow
    if (ratio < 1.15) return '#2ecc71'; // Green
    return '#e67e22'; // Orange (too fast)
  }

  closeGame() {
    this.gameEnded.emit();
  }
}
