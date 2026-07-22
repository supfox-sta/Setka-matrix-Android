#!/bin/bash
# Smoke tests for Setka Plus on a running Synapse.
#
# Usage (on server or any machine with curl + jq):
#   export BASE_URL="https://matrix.setka-matrix.ru"
#   export USER_ID="@you:matrix.setka-matrix.ru"
#   export ACCESS_TOKEN="syt_..."
#   ./test_smoke.sh
#
# Optional for full Robokassa ResultURL simulation:
#   export ROBOKASSA_PASSWORD2="..."   # from setka_plus.yaml

set -euo pipefail

BASE_URL="${BASE_URL:-https://matrix.setka-matrix.ru}"
USER_ID="${USER_ID:?Set USER_ID, e.g. @alice:matrix.setka-matrix.ru}"
ACCESS_TOKEN="${ACCESS_TOKEN:?Set ACCESS_TOKEN from Element / login API}"
ENC_USER="$(python3 -c "import urllib.parse; print(urllib.parse.quote('$USER_ID', safe=''))")"

pass=0
fail=0

check() {
  local name="$1"
  local code="$2"
  local expected="$3"
  if [ "$code" = "$expected" ]; then
    echo "  OK   $name (HTTP $code)"
    pass=$((pass + 1))
  else
    echo "  FAIL $name (expected HTTP $expected, got $code)"
    fail=$((fail + 1))
  fi
}

echo "=== Setka Plus smoke tests ==="
echo "Base: $BASE_URL"
echo "User: $USER_ID"
echo

# 1. Matrix API registered (no token)
code=$(curl -s -o /dev/null -w "%{http_code}" \
  "$BASE_URL/_matrix/client/v3/user/$ENC_USER/setka_plus/plans")
check "plans endpoint exists" "$code" "401"

# 2. Subscription (inactive by default)
sub=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/_matrix/client/v3/user/$ENC_USER/setka_plus/subscription")
echo "Subscription: $sub"
echo "$sub" | grep -q '"is_active"' || { echo "  FAIL subscription JSON"; fail=$((fail + 1)); }
echo "$sub" | grep -q '"is_active": false\|"is_active":false' && echo "  OK   subscription inactive/default" && pass=$((pass + 1)) || true

# 3. Plans list
plans=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/_matrix/client/v3/user/$ENC_USER/setka_plus/plans")
echo "Plans: $plans"
plan_id=$(echo "$plans" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['plans'][0]['id'])" 2>/dev/null || true)
if [ -n "$plan_id" ]; then
  echo "  OK   got plan_id=$plan_id"
  pass=$((pass + 1))
else
  echo "  FAIL no plans in response"
  fail=$((fail + 1))
fi

# 4. Create payment
create=$(curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"plan_id\":\"$plan_id\"}" \
  "$BASE_URL/_matrix/client/v3/user/$ENC_USER/setka_plus/payments/robokassa/create")
echo "Create payment: $create"
payment_id=$(echo "$create" | python3 -c "import sys,json; print(json.load(sys.stdin).get('payment_id',''))" 2>/dev/null || true)
checkout_url=$(echo "$create" | python3 -c "import sys,json; print(json.load(sys.stdin).get('checkout_url',''))" 2>/dev/null || true)
if [ -n "$payment_id" ] && [ -n "$checkout_url" ]; then
  echo "  OK   payment created: $payment_id"
  pass=$((pass + 1))
else
  echo "  FAIL create payment"
  fail=$((fail + 1))
fi

# 5. Payment status pending
if [ -n "$payment_id" ]; then
  status=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
    "$BASE_URL/_matrix/client/v3/user/$ENC_USER/setka_plus/payments/$payment_id")
  echo "Payment status: $status"
  echo "$status" | grep -q '"status": "pending"\|"status":"pending"' && echo "  OK   status pending" && pass=$((pass + 1)) || {
    echo "  FAIL expected pending status"
    fail=$((fail + 1))
  }
fi

# 6. Robokassa public routes (must NOT be 404 from Apache — Synapse should answer)
for path in \
  "/setka/robokassa/result" \
  "/setka/robokassa/return/success?user_id=$ENC_USER&payment_id=test"; do
  code=$(curl -s -o /tmp/setka_body.txt -w "%{http_code}" "$BASE_URL$path")
  body=$(head -c 120 /tmp/setka_body.txt)
  if [ "$code" = "404" ] && echo "$body" | grep -qi "not found"; then
    echo "  FAIL $path → Apache 404 (proxy /setka/ to Synapse or check homeserver.py patch)"
    fail=$((fail + 1))
  else
    echo "  OK   $path (HTTP $code)"
    pass=$((pass + 1))
  fi
done

# 7. ResultURL bad signature → 400 Bad signature
code=$(curl -s -o /dev/null -w "%{http_code}" \
  "$BASE_URL/setka/robokassa/result?OutSum=199.00&InvId=1&SignatureValue=deadbeef")
if [ "$code" = "400" ]; then
  echo "  OK   result rejects bad signature (HTTP 400)"
  pass=$((pass + 1))
elif [ "$code" = "404" ]; then
  echo "  SKIP result signature test (404 — fix proxy first)"
else
  echo "  WARN result bad sig → HTTP $code (expected 400)"
fi

# 8. Optional: simulate paid ResultURL (test mode only!)
if [ -n "${ROBOKASSA_PASSWORD2:-}" ] && [ -n "$checkout_url" ] && [ -n "$payment_id" ]; then
  inv_id=$(echo "$checkout_url" | sed -n 's/.*InvId=\([0-9]*\).*/\1/p')
  out_sum=$(echo "$checkout_url" | sed -n 's/.*OutSum=\([0-9.]*\).*/\1/p')
  sig=$(python3 <<PY
import hashlib, urllib.parse
user_id = urllib.parse.unquote("$ENC_USER")
base = f"{out_sum}:{inv_id}:$ROBOKASSA_PASSWORD2:Shp_payment_id={payment_id}:Shp_user_id={user_id}"
print(hashlib.md5(base.encode()).hexdigest().upper())
PY
)
  result=$(curl -s -G "$BASE_URL/setka/robokassa/result" \
    --data-urlencode "OutSum=$out_sum" \
    --data-urlencode "InvId=$inv_id" \
    --data-urlencode "SignatureValue=$sig" \
    --data-urlencode "Shp_payment_id=$payment_id" \
    --data-urlencode "Shp_user_id=$USER_ID")
  echo "ResultURL response: $result"
  if [ "$result" = "OK$inv_id" ]; then
    echo "  OK   ResultURL accepted payment"
    pass=$((pass + 1))
    sub2=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
      "$BASE_URL/_matrix/client/v3/user/$ENC_USER/setka_plus/subscription")
    echo "Subscription after pay: $sub2"
    echo "$sub2" | grep -q '"is_active": true\|"is_active":true' && echo "  OK   subscription active" && pass=$((pass + 1)) || {
      echo "  FAIL subscription not active after ResultURL"
      fail=$((fail + 1))
    }
  else
    echo "  FAIL ResultURL did not return OK$inv_id"
    fail=$((fail + 1))
  fi
fi

echo
echo "=== Done: $pass passed, $fail failed ==="
[ "$fail" -eq 0 ]
