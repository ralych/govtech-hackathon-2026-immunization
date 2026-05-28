// Root app — routes between PatientList, PatientDetail, VaccinationForm

function App() {
  const [route, setRoute] = useState({ name: "list" });
  const [showForm, setShowForm] = useState(false);
  const [justAdded, setJustAdded] = useState(null);
  // Local mutable copy of vaccinations to support new entries
  const [vaxOverride, setVaxOverride] = useState({});

  // Patch AppData.vaccinations to reflect overrides at lookup time.
  // We do this by merging the override into the global data object before rendering.
  useEffect(() => {
    const base = window.__originalVax || (window.__originalVax = JSON.parse(JSON.stringify(window.AppData.vaccinations)));
    const merged = JSON.parse(JSON.stringify(base));
    for (const [pid, extras] of Object.entries(vaxOverride)) {
      merged[pid] = [...(merged[pid] || []), ...extras];
    }
    window.AppData.vaccinations = merged;
  }, [vaxOverride]);

  const crumbs = useMemo(() => {
    if (route.name === "list") return [{ key: "list", label: "Patient:innen" }];
    if (route.name === "detail") {
      const p = window.AppData.patients.find(x => x.id === route.patientId);
      return [
        { key: "list", label: "Patient:innen" },
        { key: "detail", label: p ? `${p.lastName}, ${p.firstName}` : "Patient:in" },
      ];
    }
    return [];
  }, [route]);

  const onCrumb = (c) => {
    if (c.key === "list") setRoute({ name: "list" });
  };

  const openPatient = (id) => {
    setJustAdded(null);
    setRoute({ name: "detail", patientId: id });
  };

  const handleSubmit = (record) => {
    setVaxOverride(prev => ({
      ...prev,
      [route.patientId]: [...(prev[route.patientId] || []), record],
    }));
    setShowForm(false);
    setJustAdded({ ...record, _addedId: record.id });
    // Auto-dismiss toast highlight after a while
    setTimeout(() => setJustAdded(j => j && { ...j, _addedId: null }), 4500);
  };

  return (
    <div className="app">
      <TopBar crumbs={crumbs} onCrumb={onCrumb} />

      {route.name === "list" && (
        <PatientList onOpenPatient={openPatient} />
      )}

      {route.name === "detail" && (
        <PatientDetail
          patientId={route.patientId}
          onBack={() => setRoute({ name: "list" })}
          onAddVaccination={() => setShowForm(true)}
          justAdded={justAdded}
        />
      )}

      {showForm && route.name === "detail" && (
        <VaccinationForm
          patient={window.AppData.patients.find(p => p.id === route.patientId)}
          onCancel={() => setShowForm(false)}
          onSubmit={handleSubmit}
        />
      )}
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
