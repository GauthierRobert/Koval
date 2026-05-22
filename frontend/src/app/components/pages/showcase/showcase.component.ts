import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-showcase',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './showcase.component.html',
  styleUrl: './showcase.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShowcaseComponent {
  private readonly router = inject(Router);

  readonly tickerItems = [
    'Workout design',
    'Multi-week plans',
    'Objectives',
    'Performance analytics',
    'Club sessions',
    'MCP for Claude',
    'Coach workflow',
    'Calendar control',
    'Race briefings',
    'PMC + power curve',
  ];

  readonly mcpCapabilities = [
    {
      label: 'Plan',
      copy: 'Spin up multi-week plans, clone what works, schedule every session.',
      tools: ['createPlan', 'addDayToPlan', 'scheduleTraining', 'rescheduleWorkout'],
    },
    {
      label: 'Design',
      copy: 'Build cycling, run, swim and brick sessions with explicit zones and blocks.',
      tools: ['createTraining', 'cloneTraining', 'estimateTrainingMetrics', 'searchTrainings'],
    },
    {
      label: 'Review',
      copy: 'Read PMC, power curve, FRI and weekly volume from the same data the app uses.',
      tools: ['getAthletePmc', 'getAthletePowerCurve', 'renderPmcReport', 'renderFriReport'],
    },
    {
      label: 'Race',
      copy: 'Search races, link them to goals, brief the day from your AI client.',
      tools: ['searchRaces', 'createRace', 'createGoal', 'linkRaceToGoal'],
    },
    {
      label: 'Coach',
      copy: 'Survey athletes, assign work, drop coach notes — without leaving Claude.',
      tools: ['listAthletes', 'assignTraining', 'appendCoachNote', 'getAthleteSchedule'],
    },
    {
      label: 'Club',
      copy: 'Organize club sessions, run the activity feed, publish the gazette.',
      tools: ['createClubSession', 'postClubAnnouncement', 'publishGazetteWithPdf'],
    },
  ];

  readonly mcpPrompts = [
    'Build me a 6-week sweet-spot block before my next gravel race.',
    'How is my form tracking versus last month? Pull the PMC.',
    'Schedule tomorrow as a 90-min Z2 with 3×8 at threshold.',
    "Draft this week's club gazette from recent activity.",
  ];

  readonly mcpClients = ['Claude Desktop', 'Claude.ai', 'Claude Code', 'Any MCP-compatible client'];

  readonly mcpSetupSteps = [
    {
      step: '01',
      title: 'Run the Koval onboarding in Claude',
      copy: 'Ask Claude to run the Koval onboarding. It walks you through your sport, FTP, threshold pace, swim CSS, goals and current block, writes it all back into Koval through the MCP, and produces a profile.md you can reuse anywhere.',
      hint: 'Try: "run the Koval onboarding and give me my profile.md when you\'re done"',
    },
    {
      step: '02',
      title: 'Paste profile.md into a Claude Project pre-prompt',
      copy: 'Open a new Project in Claude.ai for your training and paste the profile.md from step 1 into the Project instructions. Claude carries that context into every conversation and pairs it with live data from Koval through the MCP.',
      hint: 'One profile.md, one Project — every new chat starts already knowing your numbers and goals.',
    },
  ];

  readonly mcpShots = [
    {
      shot: 'assets/showcase/claude.ia.1.png',
      step: '01',
      title: 'Plan from history',
      copy: 'Claude reads your recent sessions, opens the plan-training playbook, and drafts tomorrow on your real data.',
      tag: 'searchRecentSessions · loadPlaybook',
    },
    {
      shot: 'assets/showcase/claude.ia.2.png',
      step: '02',
      title: 'Adapt on the fly',
      copy: 'Conflict on the calendar? Claude proposes a swap that protects your aerobic endurance week.',
      tag: 'getAthleteSchedule · rescheduleWorkout',
    },
    {
      shot: 'assets/showcase/claude.ia.3.png',
      step: '03',
      title: 'Create the workout',
      copy: 'It computes TSS block by block and creates the session with one click — straight into your library.',
      tag: 'CreateTraining',
    },
  ];

  readonly modules = [
    {
      label: 'Weekly control',
      title: 'Training Calendar',
      copy: 'See the whole training picture at once — week or month — so session timing, rest and race prep stop colliding.',
      shot: 'assets/showcase/calendar.png',
      alt: 'Koval training calendar header with week / month toggle.',
    },
    {
      label: 'Performance clarity',
      title: 'PMC + Analytics',
      copy: 'Read CTL, ATL and form behind the plan with a dedicated analytics layer instead of guessing whether the work is landing.',
      shot: 'assets/showcase/pmc.png',
      alt: 'Performance management chart showing fitness, fatigue and form trends.',
    },
    {
      label: 'Library',
      title: 'Training Library',
      copy: 'Filter by sport and intent — VO2max, threshold, sweet spot, endurance, recovery — and reuse what works.',
      shot: 'assets/showcase/trainings.png',
      alt: 'Koval training library with sport and intent filters.',
    },
    {
      label: 'Season targets',
      title: 'Objectives',
      copy: 'Set the goals that anchor the season — link them to races, plans and key sessions so every block has a why.',
      shot: 'assets/showcase/objectives.png',
      alt: 'Koval objectives view showing season goals linked to plans and races.',
    },
    {
      label: 'Execution',
      title: 'Sessions',
      copy: 'Inspect each completed session — blocks, intensity, zone time and power curve — to know exactly what landed.',
      shot: 'assets/showcase/sessions.png',
      alt: 'Koval session detail showing blocks, zone time and power curve.',
    },
  ];

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
