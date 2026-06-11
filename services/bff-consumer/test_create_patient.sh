# test welche User bereits erfasst sind
echo "======================="
docker exec vacd-fhir-db psql -U vacduser -d chvacdapirefserver \
        -c "TRUNCATE fhir_resource, fhir_resource_audit, revinfo RESTART IDENTITY CASCADE;"
echo "Alle Patienten gelöscht"

echo "Aktuelle Patienten ..."
docker exec vacd-fhir-db psql -U vacduser -d chvacdapirefserver \
        -c "SELECT id, resource_id, json FROM fhir_resource WHERE resource_type='Patient';"



function show_patient {
        echo "======================="
        login="$1"
        echo "Hole Infos zu Patient '$login' ..."
        user_id=$(awk -F'"' "/\"$login\"/ {print \$10}" ../../services/iam-mock/server.py)
        echo "> hat user_id=$user_id"

        curl http://localhost:8001/patients/$user_id
        echo ""
        echo ""
}


# erstelle Patient 1    (gemäss Mock: USERS in services/iam-mock/server.py)

show_patient "patient1"
show_patient "patient2"
show_patient "patient3"
show_patient "patient4"


# test ob User "patient1" nun erfasst ist
echo "======================="
echo "Aktuelle Patienten ..."

docker exec vacd-fhir-db psql -U vacduser -d chvacdapirefserver \
        -c "SELECT * FROM fhir_resource_audit ORDER BY id DESC LIMIT 5;"
