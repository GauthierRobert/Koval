import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, inject, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-zombie-game',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="game-viewport">
      <div class="game-background" [style.background-position-x.px]="bgPos"></div>
      
      <!-- HUD -->
      <div class="game-hud">
        <div class="lives">
          <span *ngFor="let life of [1,2,3]; let i = index" 
                class="heart" 
                [class.lost]="i >= lives">❤️</span>
        </div>
        <div class="power-meter">
          <div class="label">TARGET: {{ targetPower }}W</div>
          <div class="current-power" [style.color]="getPowerColor()">{{ currentPower }}W</div>
        </div>
        <div class="warning" *ngIf="isTooFast">TOO FAST! RELAX!</div>
      </div>

      <!-- Entities -->
      <div class="entities">
        <div class="cyclist" [style.left.px]="playerX">
          <img src="assets/player.png" alt="cyclist">
        </div>
        <div class="zombie" [style.left.px]="zombieX">
          <img src="assets/zombie.png" alt="zombie">
        </div>
      </div>

      <div class="game-overlay" *ngIf="lives === 0">
        <h2>GAME OVER</h2>
        <p>Stay focused on the workout!</p>
        <button (click)="closeGame()">RETURN TO DASHBOARD</button>
      </div>
    </div>
  `,
  styles: [`
    .game-viewport {
      width: 100%;
      height: 100%;
      position: relative;
      overflow: hidden;
      background: #000;
      border-radius: 20px;
    }
    .game-background {
      position: absolute;
      inset: 0;
      background-image: url("/assets/game_bg.png");
      background-size: auto 100%;
      background-repeat: repeat-x;
      opacity: 0.6;
    }
    .game-hud {
      position: absolute;
      top: 20px;
      left: 20px;
      right: 20px;
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      z-index: 10;
    }
    .heart { font-size: 24px; margin-right: 5px; transition: opacity 0.3s; }
    .heart.lost { opacity: 0.2; filter: grayscale(1); }
    
    .power-meter {
      background: rgba(0,0,0,0.8);
      padding: 10px 20px;
      border-radius: 12px;
      border: 1px solid rgba(255,255,255,0.1);
      text-align: right;
    }
    .power-meter .label { font-size: 10px; color: #888; font-weight: 700; }
    .power-meter .current-power { font-size: 24px; font-weight: 800; }

    .warning {
      position: absolute;
      top: 80px;
      left: 50%;
      transform: translateX(-50%);
      background: #e74c3c;
      color: white;
      padding: 5px 15px;
      border-radius: 5px;
      font-weight: 800;
      animation: flash 0.5s infinite alternate;
    }
    @keyframes flash { from { opacity: 0.5; } to { opacity: 1; } }

    .entities {
      position: absolute;
      bottom: 60px;
      width: 100%;
      height: 150px;
    }
    .cyclist, .zombie {
      position: absolute;
      bottom: 0;
      width: 150px;
      transition: left 0.1s linear;
    }
    .cyclist img, .zombie img { width: 100%; height: auto; }
    
    .game-overlay {
      position: absolute;
      inset: 0;
      background: rgba(0,0,0,0.9);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      z-index: 20;
    }
    .game-overlay h2 { font-size: 48px; font-weight: 800; color: #e74c3c; margin-bottom: 10px; }
    .game-overlay button {
      background: white;
      color: black;
      border: none;
      padding: 12px 30px;
      border-radius: 25px;
      font-weight: 700;
      cursor: pointer;
    }
  `]
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
