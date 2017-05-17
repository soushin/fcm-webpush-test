#! /bin/bash

DOCKER_COMPOSE_COMMAND=`which docker-compose`

if [ ! -x $DOCKER_COMPOSE_COMMAND ]; then
  echo "error, should install docker-compose" >&2
  exit -1
fi

if [ -z "$1" -o -z "$2" ]; then
  echo "USAGE: ./setup.sh <GCM_SENDER_ID> <FCM_SERVER_KEY>" >&2
  exit -1
fi

sed -i.org  -e "s/{FCM_SERVER_KEY}/$2/" ./docker-compose.yml
rm ./docker-compose.yml.org
sed -i.org  -e "s/{GCM_SENDER_ID}/$1/" ./web/app/manifest.json
rm ./web/app/manifest.json.org
