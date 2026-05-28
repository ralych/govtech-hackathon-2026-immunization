import type { DiseaseGroup, Relation, Vaccination } from './types';

const FULL_DATE: Intl.DateTimeFormatOptions = { day: '2-digit', month: 'short', year: 'numeric' };
const SHORT_DATE: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit', year: 'numeric' };

/** Format an ISO date for Swiss-German display. Falls back to the raw string if unparseable. */
export function formatDate(iso: string, opts: { short?: boolean } = {}): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('de-CH', opts.short ? SHORT_DATE : FULL_DATE);
}

/** Whole years between a date of birth and today. */
export function calcAge(dobIso: string): number {
  const dob = new Date(dobIso);
  const now = new Date();
  let age = now.getFullYear() - dob.getFullYear();
  const monthDiff = now.getMonth() - dob.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < dob.getDate())) age--;
  return age;
}

export function initials(firstName: string, lastName: string): string {
  return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();
}

const RELATION_LABEL: Record<Relation, string> = {
  self: 'Ich',
  son: 'Sohn',
  daughter: 'Tochter',
};

export function relationLabel(relation: Relation): string {
  return RELATION_LABEL[relation];
}

/** Group vaccinations by target disease; entries ascending by date, groups by most-recent dose. */
export function groupByDisease(vaccinations: readonly Vaccination[]): DiseaseGroup[] {
  const byDisease = new Map<string, Vaccination[]>();
  for (const v of vaccinations) {
    const bucket = byDisease.get(v.disease);
    if (bucket) bucket.push(v);
    else byDisease.set(v.disease, [v]);
  }

  const groups: DiseaseGroup[] = [];
  for (const [disease, entries] of byDisease) {
    entries.sort((a, b) => a.date.localeCompare(b.date));
    groups.push({ disease, entries, latest: entries[entries.length - 1].date });
  }
  groups.sort((a, b) => b.latest.localeCompare(a.latest));
  return groups;
}
