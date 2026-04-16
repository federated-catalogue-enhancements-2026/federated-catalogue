#!/bin/bash
set +H
curl -s -X POST 'http://key-server:8080/realms/gaia-x/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=federated-catalogue' \
  --data-urlencode 'client_secret=mQsjH8DAYbJueI227a5XhLTyQTokZr3S' \
  --data-urlencode 'username=saackef' \
  --data-urlencode 'password=Test-123' \
  | tr ',' '\n' | grep access_token | cut -d'"' -f4 | tr -d '\n'