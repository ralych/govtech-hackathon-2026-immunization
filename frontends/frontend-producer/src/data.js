const doctor = {
  name: "Dr. med. Sarah Müller",
  title: "Fachärztin Allgemeine Innere Medizin FMH",
  gln: "7601000123456",
  praxis: "Hausarztpraxis Bahnhofstrasse",
  address: "Bahnhofstrasse 47, 8001 Zürich",
  initials: "SM",
};

window.AppData = { doctor };

// Catalog used in form dropdowns
const vaccineCatalog = [
  "Boostrix",
  "Boostrix Polio",
  "Comirnaty (BNT162b2)",
  "Comirnaty Omicron XBB.1.5",
  "Efluelda (HD)",
  "Encepur",
  "Engerix-B",
  "FSME-Immun CC",
  "Fluarix Tetra",
  "Gardasil 9",
  "Havrix 1440",
  "Influvac Tetra",
  "Infanrix-IPV",
  "Infanrix hexa",
  "Menveo",
  "MMR-VaxPro",
  "Nimenrix",
  "Pentavac",
  "Prevenar 13",
  "Priorix",
  "Rabipur",
  "Shingrix",
  "Spikevax (mRNA-1273)",
  "Stamaril",
  "Td-pur",
  "Twinrix",
  "Varilrix",
];

const manufacturers = [
  "Bavarian Nordic",
  "GlaxoSmithKline",
  "Moderna",
  "MSD",
  "Pfizer",
  "Pfizer-BioNTech",
  "Sanofi",
  "Viatris",
];

const routes = [
  { value: "i.m.", label: "i.m. (intramuskulär)" },
  { value: "s.c.", label: "s.c. (subkutan)" },
  { value: "i.d.", label: "i.d. (intradermal)" },
  { value: "oral", label: "oral" },
  { value: "nasal", label: "nasal" },
];

const sites = [
  "Oberarm links (M. deltoideus)",
  "Oberarm rechts (M. deltoideus)",
  "Oberschenkel links (M. vastus lateralis)",
  "Oberschenkel rechts (M. vastus lateralis)",
  "Gesäss links",
  "Gesäss rechts",
];

const reasons = [
  "Grundimmunisierung",
  "Auffrischimpfung (Booster)",
  "Nachholimpfung",
  "Reiseimpfung",
  "Risikogruppe — beruflich",
  "Risikogruppe — medizinisch",
  "Postexpositionsprophylaxe",
  "Saisonale Impfung",
];

window.AppData = { doctor, vaccineCatalog, manufacturers, routes, sites, reasons };
