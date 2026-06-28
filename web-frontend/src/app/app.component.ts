import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

type SessionMode = 'COMPARE_BOTH' | 'FRESH_ONLY' | 'LEARNED_ONLY';
type PresetName = 'STANDARD' | 'CONSERVATIVE' | 'HIGH_RISK';

interface PatientCase {
  childId: number;
  ageInMonths: number;
  rr1: number;
  spo21: number;
  chest1: boolean;
  grunting1: boolean;
  apnea1: boolean;
  cyanosis1: boolean;
  rr2: number;
  spo22: number;
  chest2: boolean;
  grunting2: boolean;
  apnea2: boolean;
  cyanosis2: boolean;
  intakePercent: number;
  poorFeeding: boolean;
}

interface HistoryEntry {
  createdAt: string;
  report: string;
}

interface RuleRunResponse {
  patientCase: PatientCase;
  sessionMode: SessionMode;
  rawReport: string;
  htmlReport: string;
}

interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  message: string;
}

interface ParsedSession {
  title: string;
  summary: string[];
  activatedRules: string[];
  derivedFacts: string[];
  finalDecision: string[];
  queryGroups: Array<{ title: string; lines: string[] }>;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly apiBase = '/api';

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly runBusy = signal(false);
  readonly patients = signal<PatientCase[]>([]);
  readonly selectedPatientId = signal<number | null>(null);
  readonly history = signal<HistoryEntry[]>([]);
  readonly output = signal('');
  readonly defenseHtml = signal<SafeHtml | null>(null);
  readonly connectionStatus = signal('Loading API status...');
  readonly feedback = signal('');
  readonly preset = signal<PresetName>('STANDARD');
  readonly sessionMode = signal<SessionMode>('COMPARE_BOTH');
  readonly activeView = signal<'report' | 'defense' | 'history'>('report');

  readonly form = signal<PatientCase>(this.createPreset('STANDARD'));

  readonly selectedPatient = computed(
    () => this.patients().find((patient) => patient.childId === this.selectedPatientId()) ?? null,
  );

  readonly parsedSessions = computed(() => this.parseReport(this.output()));

  constructor() {
    void this.loadInitialState();
  }

  loadPatient(patient: PatientCase): void {
    this.selectedPatientId.set(patient.childId);
    this.form.set(structuredClone(patient));
    this.feedback.set(`Patient ${patient.childId} loaded.`);
  }

  applyPreset(name: PresetName): void {
    const current = this.form();
    const presetCase = this.createPreset(name);
    this.preset.set(name);
    this.form.set({
      ...presetCase,
      childId: current.childId,
      ageInMonths: current.ageInMonths,
    });
    this.feedback.set(`Applied ${this.labelForPreset(name).toLowerCase()}.`);
  }

  async savePatient(): Promise<void> {
    this.saving.set(true);
    this.feedback.set('Saving patient...');
    try {
      const patient = structuredClone(this.form());
      const response = await this.post<PatientCase>(`${this.apiBase}/patients`, patient);
      this.upsertPatient(response);
      this.selectedPatientId.set(response.childId);
      this.feedback.set(`Patient ${response.childId} saved.`);
    } finally {
      this.saving.set(false);
    }
  }

  async runRules(): Promise<void> {
    this.runBusy.set(true);
    this.activeView.set('report');
    this.feedback.set('Running rules...');
    try {
      const payload = {
        patientCase: structuredClone(this.form()),
        sessionMode: this.sessionMode(),
        persistPatient: true,
      };
      const response = await this.post<RuleRunResponse>(`${this.apiBase}/rules/run`, payload);
      this.output.set(response.rawReport);
      this.defenseHtml.set(this.sanitizer.bypassSecurityTrustHtml(response.htmlReport));
      this.upsertPatient(response.patientCase);
      this.selectedPatientId.set(response.patientCase.childId);
      await this.loadHistory(response.patientCase.childId, false);
      this.feedback.set(`Rules executed for patient ${response.patientCase.childId}.`);
    } finally {
      this.runBusy.set(false);
    }
  }

  async showHistory(): Promise<void> {
    const childId = this.form().childId;
    await this.loadHistory(childId, true);
  }

  async resetLearnedSession(): Promise<void> {
    this.feedback.set('Resetting learned session...');
    await this.post<void>(`${this.apiBase}/rules/reset-learned-session`, {});
    this.feedback.set('Learned session reset. Future learned runs start clean.');
  }

  async refreshPatients(): Promise<void> {
    await this.loadPatients();
    this.feedback.set('Patient list refreshed.');
  }

  labelForPreset(name: PresetName): string {
    return {
      STANDARD: 'Standard preset',
      CONSERVATIVE: 'Conservative preset',
      HIGH_RISK: 'High-risk preset',
    }[name];
  }

  labelForMode(mode: SessionMode): string {
    return {
      COMPARE_BOTH: 'Compare both sessions',
      FRESH_ONLY: 'Fresh session only',
      LEARNED_ONLY: 'Learned session only',
    }[mode];
  }

  trackByPatientId(_: number, patient: PatientCase): number {
    return patient.childId;
  }

  private async loadInitialState(): Promise<void> {
    this.loading.set(true);
    try {
      await Promise.all([this.loadHealth(), this.loadPatients()]);
      const first = this.patients()[0];
      if (first) {
        this.loadPatient(first);
      }
    } finally {
      this.loading.set(false);
    }
  }

  private async loadHealth(): Promise<void> {
    const response = await this.get<string>(`${this.apiBase}/health`);
    this.connectionStatus.set(response);
  }

  private async loadPatients(): Promise<void> {
    const response = await this.get<PatientCase[]>(`${this.apiBase}/patients`);
    this.patients.set(response);
  }

  private async loadHistory(childId: number, activateView: boolean): Promise<void> {
    const response = await this.get<HistoryEntry[]>(`${this.apiBase}/patients/${childId}/history`);
    this.history.set(response);
    if (activateView) {
      this.activeView.set('history');
      this.feedback.set(response.length ? `History loaded for patient ${childId}.` : `No history yet for patient ${childId}.`);
    }
  }

  private upsertPatient(patient: PatientCase): void {
    const next = [...this.patients()];
    const index = next.findIndex((entry) => entry.childId === patient.childId);
    if (index >= 0) {
      next[index] = patient;
    } else {
      next.push(patient);
      next.sort((a, b) => a.childId - b.childId);
    }
    this.patients.set(next);
    this.form.set(structuredClone(patient));
  }

  private createPreset(name: PresetName): PatientCase {
    if (name === 'CONSERVATIVE') {
      return {
        childId: 1,
        ageInMonths: 10,
        rr1: 42,
        spo21: 97,
        chest1: false,
        grunting1: false,
        apnea1: false,
        cyanosis1: false,
        rr2: 40,
        spo22: 97,
        chest2: false,
        grunting2: false,
        apnea2: false,
        cyanosis2: false,
        intakePercent: 90,
        poorFeeding: false,
      };
    }

    if (name === 'HIGH_RISK') {
      return {
        childId: 1,
        ageInMonths: 10,
        rr1: 62,
        spo21: 89,
        chest1: true,
        grunting1: true,
        apnea1: true,
        cyanosis1: false,
        rr2: 68,
        spo22: 88,
        chest2: true,
        grunting2: true,
        apnea2: false,
        cyanosis2: true,
        intakePercent: 45,
        poorFeeding: true,
      };
    }

    return {
      childId: 1,
      ageInMonths: 10,
      rr1: 54,
      spo21: 95,
      chest1: true,
      grunting1: false,
      apnea1: false,
      cyanosis1: false,
      rr2: 58,
      spo22: 94,
      chest2: true,
      grunting2: true,
      apnea2: false,
      cyanosis2: false,
      intakePercent: 60,
      poorFeeding: true,
    };
  }

  private parseReport(report: string): ParsedSession[] {
    if (!report.trim()) {
      return [];
    }

    return report.split('\n\n========================================\n\n').map((block) => {
      const lines = block.split(/\r?\n/);
      const session: ParsedSession = {
        title: lines[0] || 'Session',
        summary: [],
        activatedRules: [],
        derivedFacts: [],
        finalDecision: [],
        queryGroups: [],
      };

      let section: 'summary' | 'rules' | 'facts' | 'decision' | 'queries' = 'summary';
      let currentQuery: { title: string; lines: string[] } | null = null;

      for (const rawLine of lines.slice(1)) {
        const line = rawLine.trim();
        if (!line) {
          continue;
        }
        if (line === 'Activated rules') {
          section = 'rules';
          continue;
        }
        if (line === 'Derived facts') {
          section = 'facts';
          continue;
        }
        if (line === 'Final decision') {
          section = 'decision';
          continue;
        }
        if (line === 'Queries') {
          section = 'queries';
          continue;
        }

        if (section === 'summary') {
          session.summary.push(line);
          continue;
        }
        if (section === 'rules') {
          session.activatedRules.push(this.stripBullet(line));
          continue;
        }
        if (section === 'facts') {
          session.derivedFacts.push(this.stripBullet(line));
          continue;
        }
        if (section === 'decision') {
          session.finalDecision.push(this.stripBullet(line));
          continue;
        }

        const isHeader = !line.startsWith('-') && !line.startsWith('rows:');
        if (isHeader) {
          currentQuery = { title: line, lines: [] };
          session.queryGroups.push(currentQuery);
        } else if (currentQuery) {
          currentQuery.lines.push(line);
        }
      }

      return session;
    });
  }

  private stripBullet(line: string): string {
    return line.startsWith('- ') ? line.slice(2) : line;
  }

  private async get<T>(url: string): Promise<T> {
    const envelope = await fetch(url).then(async (response) => {
      const json = (await response.json()) as ApiEnvelope<T>;
      if (!response.ok || !json.success) {
        throw new Error(json.message || 'Request failed.');
      }
      return json;
    });
    return envelope.data;
  }

  private async post<T>(url: string, body: unknown): Promise<T> {
    const envelope = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }).then(async (response) => {
      const json = (await response.json()) as ApiEnvelope<T>;
      if (!response.ok || !json.success) {
        throw new Error(json.message || 'Request failed.');
      }
      return json;
    });
    return envelope.data;
  }
}
