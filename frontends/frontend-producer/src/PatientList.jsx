// Screen 1 — Patient list with search & filter

function PatientList({ onOpenPatient }) {
  const { patients } = window.AppData;
  const [query, setQuery] = useState("");
  const [sex, setSex] = useState("all"); // all | M | F
  const [ageBand, setAgeBand] = useState("all"); // all | child | adult | senior
  const [sortBy, setSortBy] = useState("name"); // name | age

  const enriched = useMemo(() => patients.map((p) => {
    return { ...p, age: calcAge(p.dob) };
  }), [patients]);

  const filtered = useMemo(() => {
    let list = enriched;
    if (sex !== "all") list = list.filter((p) => p.sex === sex);
    if (ageBand !== "all") {
      list = list.filter((p) => {
        if (ageBand === "child") return p.age < 18;
        if (ageBand === "adult") return p.age >= 18 && p.age < 65;
        if (ageBand === "senior") return p.age >= 65;
        return true;
      });
    }
    if (query.trim()) {
      const q = query.trim().toLowerCase();
      list = list.filter((p) =>
      p.firstName.toLowerCase().includes(q) ||
      p.lastName.toLowerCase().includes(q) ||
      p.email.toLowerCase().includes(q) ||
      p.address.toLowerCase().includes(q) ||
      p.phone.replace(/\s+/g, "").includes(q.replace(/\s+/g, "")) ||
      p.dob.includes(q) ||
      p.id.toLowerCase().includes(q)
      );
    }
    if (sortBy === "name") list = [...list].sort((a, b) => a.lastName.localeCompare(b.lastName));
    if (sortBy === "age") list = [...list].sort((a, b) => b.age - a.age);
    return list;
  }, [enriched, query, sex, ageBand, sortBy]);

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <h1 className="page-title">Patient:innen</h1>
        </div>
      </div>

      {/* Search + filters bar */}
      <div className="filters-bar">
        <div className="search-wrap">
          <Icon.Search className="search-icon" />
          <input
            className="search-input"
            placeholder="Suche nach..."
            value={query}
            onChange={(e) => setQuery(e.target.value)} />
          
          {query &&
          <button className="search-clear" onClick={() => setQuery("")} aria-label="Suche leeren">
              <Icon.Close />
            </button>
          }
          <div className="kbd-hint">⌘K</div>
        </div>

        <div className="chip-group" role="tablist" aria-label="Geschlecht">
          {[
          { v: "all", l: "Alle" },
          { v: "F", l: "Weiblich" },
          { v: "M", l: "Männlich" }].
          map((o) =>
          <button key={o.v} className={"chip" + (sex === o.v ? " is-active" : "")} onClick={() => setSex(o.v)}>{o.l}</button>
          )}
        </div>

        <div className="chip-group" role="tablist" aria-label="Altersgruppe">
          {[
          { v: "all", l: "Alle Alter" },
          { v: "child", l: "0–17" },
          { v: "adult", l: "18–64" },
          { v: "senior", l: "65+" }].
          map((o) =>
          <button key={o.v} className={"chip" + (ageBand === o.v ? " is-active" : "")} onClick={() => setAgeBand(o.v)}>{o.l}</button>
          )}
        </div>

        <div className="sort-wrap-quiet">
            <select className="sort-select-quiet" value={sortBy} onChange={(e) => setSortBy(e.target.value)} aria-label="Sortieren">
              <option value="name">Sortiert: Nachname A–Z</option>
              <option value="age">Sortiert: Alter</option>
            </select>
        </div>
      </div>

      {/* Result count */}
      <div className="result-meta">
        <span>{filtered.length} Treffer</span>
        {(query || sex !== "all" || ageBand !== "all") &&
        <button className="reset-link" onClick={() => {setQuery("");setSex("all");setAgeBand("all");}}>
            Filter zurücksetzen
          </button>
        }
      </div>

      {/* Patient table */}
      <div className="card patient-table-card">
        <table className="patient-table">
          <thead>
            <tr>
              <th style={{ width: "36%" }}>Patient:in</th>
              <th style={{ width: "20%" }}>Geburtsdatum</th>
              <th style={{ width: "40%" }}>Adresse</th>
              <th style={{ width: "4%" }}></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((p) =>
            <tr key={p.id} className="patient-row" onClick={() => onOpenPatient(p.id)} tabIndex={0}
            onKeyDown={(e) => {if (e.key === "Enter") onOpenPatient(p.id);}}>
                <td>
                  <div className="cell-patient">
                    <PatientAvatar patient={p} />
                    <div className="cell-patient-text">
                      <div className="name-line">
                        <span className="last">{p.lastName}</span>, <span className="first">{p.firstName}</span>
                      </div>
                      <div className="sub-line">{p.sex === "F" ? "weiblich" : "männlich"}</div>
                    </div>
                  </div>
                </td>
                <td>
                  <div className="dob-line tnum">{formatDate(p.dob, { short: true })}</div>
                  <div className="sub-line">{p.age} Jahre</div>
                </td>
                <td>
                  <div className="addr-line">{p.address}</div>
                </td>
                <td>
                  <div className="row-cta"><Icon.Chevron /></div>
                </td>
              </tr>
            )}
            {filtered.length === 0 &&
            <tr>
                <td colSpan={4}>
                  <div className="empty-state">
                    <div className="empty-icon"><Icon.Search /></div>
                    <div className="empty-title">Keine Patient:innen gefunden</div>
                    <div className="empty-sub">Passe deine Suche oder die Filter an.</div>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </main>);

}

window.PatientList = PatientList;