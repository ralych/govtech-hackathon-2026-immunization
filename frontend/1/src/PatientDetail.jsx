// Screen 2 — Patient vaccination history (Swiss Impfausweis style)

function PatientDetail({ patientId, onBack, onAddVaccination, justAdded }) {
  const { patients, vaccinations } = window.AppData;
  const patient = patients.find((p) => p.id === patientId);
  const records = vaccinations[patientId] || [];

  if (!patient) return <div className="page">Patient:in nicht gefunden.</div>;

  // Group by target disease, sort chronologically within group.
  const groups = useMemo(() => {
    const map = new Map();
    for (const r of records) {
      if (!map.has(r.disease)) map.set(r.disease, []);
      map.get(r.disease).push(r);
    }
    // Sort each group ascending by date
    for (const arr of map.values()) arr.sort((a, b) => a.date.localeCompare(b.date));
    // Group order: most recent activity first
    const groupArr = [...map.entries()].map(([disease, items]) => ({
      disease,
      items,
      latest: items[items.length - 1].date
    }));
    groupArr.sort((a, b) => b.latest.localeCompare(a.latest));
    return groupArr;
  }, [records]);

  const stats = useMemo(() => {
    const total = records.length;
    const diseases = new Set(records.map((r) => r.disease)).size;
    const recent = records.slice().sort((a, b) => b.date.localeCompare(a.date))[0];
    return { total, diseases, recent };
  }, [records]);

  return (
    <main className="page">
      {/* Back link */}
      <button className="back-link" onClick={onBack}>
        <Icon.Back /> Zurück zur Patient:innenliste
      </button>

      {justAdded &&
      <div className="toast-success">
          <div className="toast-icon"><Icon.Check /></div>
          <div>
            <div className="toast-title">Impfung erfasst</div>
            <div className="toast-sub">
              {justAdded.vaccine} · {formatDate(justAdded.date, { short: true })} wurde dem Dossier hinzugefügt.
            </div>
          </div>
        </div>
      }

      {/* Patient hero */}
      <section className="patient-hero">
        <div className="patient-hero-left">
          <PatientAvatar patient={patient} size={64} />
          <div>
            <div className="patient-name-line">
              <h1 className="patient-name">{patient.lastName}, {patient.firstName}</h1>
            </div>
            <div className="patient-meta">
              <span><Icon.Cake /> {formatDate(patient.dob, { short: true })} <em className="muted">·</em> {patient.age ?? calcAge(patient.dob)} Jahre <em className="muted">·</em> {patient.sex === "F" ? "weiblich" : "männlich"}</span>
              <span><Icon.Pin /> {patient.address}</span>
              <span><Icon.Mail /> {patient.email}</span>
              <span><Icon.Phone /> <span className="tnum">{patient.phone}</span></span>
              <span><Icon.User /> AHV <span className="mono">{patient.ahv}</span></span>
            </div>
          </div>
        </div>

        <div className="patient-hero-right">
          <div className="hero-stat-naked">
            <div className="hero-stat-value tnum">{stats.total}</div>
            <div className="hero-stat-label">Impfungen total</div>
          </div>
        </div>
      </section>

      {/* Action bar */}
      <div className="detail-actions">
        <div className="detail-actions-title">
          <h2 className="section-title">Impfausweis</h2>
        </div>
        <div className="detail-actions-right">
          <button className="btn btn-primary" onClick={onAddVaccination}><Icon.Plus /> Neue Impfung erfassen</button>
        </div>
      </div>

      {/* Vaccination groups */}
      <div className="vax-groups">
        {groups.map((g) =>
        <VaccinationGroup key={g.disease} group={g} justAddedId={justAdded?._addedId} />
        )}
        {groups.length === 0 &&
        <div className="card" style={{ padding: 32, textAlign: "center", color: "var(--text-3)" }}>
            Noch keine Impfungen erfasst. Klicke auf „Neue Impfung erfassen".
          </div>
        }
      </div>
    </main>);

}

function VaccinationGroup({ group, justAddedId }) {
  return (
    <section className="vax-group card">
      <header className="vax-group-head">
        <div className="vax-group-title-wrap">
          <div className="vax-target-icon"><Icon.Syringe /></div>
          <div>
            <div className="vax-group-title">{group.disease}</div>
            <div className="vax-group-meta">
              {group.items.length} {group.items.length === 1 ? "Eintrag" : "Einträge"}
              {" · "}letzte Dosis {formatDate(group.latest, { short: true })}
            </div>
          </div>
        </div>
        <span className="badge badge-accent"><Icon.Check /> Geschützt</span>
      </header>

      <ol className="vax-timeline">
        {group.items.map((r, i) =>
        <li key={r.id} className={"vax-entry" + (justAddedId === r.id ? " is-new" : "")}>
            <div className="vax-tl-dot" aria-hidden="true" />
            <div className="vax-entry-card">
              <div className="vax-entry-main">
                <div className="vax-entry-headline">
                  <span className="vax-vaccine">{r.vaccine}</span>
                  <span className="vax-dose-pill">{r.dose}</span>
                  {justAddedId === r.id && <span className="badge badge-success">Neu</span>}
                </div>
                <div className="vax-entry-meta-row">
                  <span><b className="tnum">{formatDate(r.date)}</b></span>
                  <span className="dot">·</span>
                  <span>{r.manufacturer}</span>
                  <span className="dot">·</span>
                  <span>Charge <span className="mono">{r.batch}</span></span>
                </div>
                <div className="vax-entry-meta-row sub">
                  <span>{r.route}</span>
                  <span className="dot">·</span>
                  <span>{r.site}</span>
                  {r.note && <><span className="dot">·</span><span>{r.note}</span></>}
                </div>
              </div>
              <div className="vax-entry-side">
                <div className="vax-entry-doc">Dr. S. Müller</div>
                <div className="vax-entry-gln mono">GLN 7601…3456</div>
              </div>
            </div>
          </li>
        )}
      </ol>
    </section>);

}

window.PatientDetail = PatientDetail;