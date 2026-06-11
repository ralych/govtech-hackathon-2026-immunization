# Alle Patienten anzeigen
echo "======================="
echo "Aktuelle Patienten ..."

docker exec vacd-fhir-db psql -U vacduser -d chvacdapirefserver \
        -c "SELECT * FROM fhir_resource_audit ORDER BY id DESC LIMIT 5;"
