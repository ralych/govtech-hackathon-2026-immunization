# Patienten löschen
echo "======================="
docker exec vacd-fhir-db psql -U vacduser -d chvacdapirefserver \
        -c "TRUNCATE fhir_resource, fhir_resource_audit, revinfo RESTART IDENTITY CASCADE;"
echo "Alle Patienten gelöscht"
