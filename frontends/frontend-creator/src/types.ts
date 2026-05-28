// Domain contracts for the Impfdossier mobile app.

export type Relation = 'self' | 'son' | 'daughter';

export type Sex = 'M' | 'F';

/** A single administered vaccination dose. Mirrors the Swiss Impfausweis / FHIR Immunization shape. */
export interface Vaccination {
  readonly id: string;
  /** Target disease(s), e.g. "Masern, Mumps, Röteln". Used to group entries. */
  readonly disease: string;
  /** Trade name of the administered product, e.g. "Priorix". */
  readonly vaccine: string;
  /** ISO date (yyyy-mm-dd) the dose was given. */
  readonly date: string;
  /** Dose label within a series, e.g. "1/3" or "Booster". */
  readonly dose: string;
  readonly manufacturer: string;
  readonly batch: string;
  /** Route of administration, e.g. "i.m.". */
  readonly route: string;
  /** Anatomical site, e.g. "Oberarm links". */
  readonly site: string;
  readonly note?: string;
}

/** A person whose vaccination record can be viewed in the app. */
export interface FamilyMember {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  /** ISO date of birth (yyyy-mm-dd). */
  readonly dob: string;
  readonly sex: Sex;
  /** Relationship to the account holder. Drives the switcher label. */
  readonly relation: Relation;
  readonly vaccinations: readonly Vaccination[];
}

/** Vaccinations for one disease, sorted oldest-first, with the most recent date precomputed. */
export interface DiseaseGroup {
  readonly disease: string;
  readonly entries: readonly Vaccination[];
  readonly latest: string;
}
