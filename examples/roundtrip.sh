#!/usr/bin/env bash
#
# Round-trip each example through fhir-server-1.
#
# Two checks per Bundle:
#   1. POST the whole document Bundle to /Bundle  → confirms HAPI parses it
#      as a valid FHIR R4 document Bundle.
#   2. For each entry whose resourceType has a provider on the ref server
#      (Patient, Practitioner, Organization, PractitionerRole, Immunization
#      — Composition + Binary are intentionally not exposed individually;
#      see application.yml fhir.providers), POST the resource and GET it
#      back. Asserts the round-tripped resource has the same resourceType.
#
# This is the lightest possible "is the example well-formed" check —
# the reference server stores resources as JSON blobs with no profile
# validation, so a clean round-trip proves the JSON parses as FHIR R4
# and the cross-references inside resolve to resource types HAPI accepts.
#
# Usage:  ./roundtrip.sh             # all examples
#         ./roundtrip.sh 02-*.json   # one specific file
#
# Env: FHIR_BASE (default: the dev container's service-name URL)

set -euo pipefail

FHIR_BASE="${FHIR_BASE:-http://fhir-server-1:9111/ch-vacd-api-reference-server/fhir}"
SUPPORTED='Patient|Practitioner|Organization|PractitionerRole|Immunization'

cd "$(dirname "$0")"

if [[ $# -gt 0 ]]; then
  files=("$@")
else
  files=( *.json )
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
fails=0

for bundle in "${files[@]}"; do
  [[ -f "$bundle" ]] || { echo "skip: $bundle (not a file)"; continue; }
  echo
  echo "=== $bundle ==="

  # ── 1) POST whole Bundle ────────────────────────────────────────────
  code=$(curl -sS -o "$work/bundle.resp" -w '%{http_code}' \
          -X POST "$FHIR_BASE/Bundle" \
          -H 'Content-Type: application/fhir+json' \
          --data-binary "@$bundle")
  if [[ "$code" == "201" || "$code" == "200" ]]; then
    bid=$(jq -r '.id // empty' "$work/bundle.resp")
    printf "  %-26s %s\n" "POST /Bundle" "OK (id=$bid, http $code)"
  else
    printf "  %-26s %s\n" "POST /Bundle" "FAIL (http $code)"
    head -c 200 "$work/bundle.resp"; echo
    fails=$((fails+1))
  fi

  # ── 2) Round-trip each individually-supported entry ──────────────────
  count=$(jq '.entry | length' "$bundle")
  for i in $(seq 0 $((count-1))); do
    rtype=$(jq -r ".entry[$i].resource.resourceType" "$bundle")
    if ! grep -qE "^($SUPPORTED)$" <<< "$rtype"; then
      printf "  %-26s %s\n" "$rtype" "skipped (no provider on ref server)"
      continue
    fi

    jq -c ".entry[$i].resource" "$bundle" > "$work/r.json"

    code=$(curl -sS -o "$work/post.resp" -w '%{http_code}' \
            -X POST "$FHIR_BASE/$rtype" \
            -H 'Content-Type: application/fhir+json' \
            --data-binary "@$work/r.json")
    if [[ "$code" != "201" && "$code" != "200" ]]; then
      printf "  %-26s %s\n" "POST /$rtype" "FAIL (http $code)"
      head -c 200 "$work/post.resp"; echo
      fails=$((fails+1)); continue
    fi
    assigned=$(jq -r '.id // empty' "$work/post.resp")

    code=$(curl -sS -o "$work/get.resp" -w '%{http_code}' \
            "$FHIR_BASE/$rtype/$assigned")
    if [[ "$code" != "200" ]]; then
      printf "  %-26s %s\n" "GET /$rtype/$assigned" "FAIL (http $code)"
      fails=$((fails+1)); continue
    fi
    got=$(jq -r '.resourceType' "$work/get.resp")
    if [[ "$got" != "$rtype" ]]; then
      printf "  %-26s %s\n" "$rtype/$assigned" "FAIL (got resourceType=$got)"
      fails=$((fails+1)); continue
    fi
    printf "  %-26s %s\n" "$rtype/$assigned" "OK (POST+GET)"
  done
done

echo
if [[ $fails -gt 0 ]]; then
  echo "FAIL: $fails error(s)"
  exit 1
else
  echo "All round-trips green."
fi
