#!/bin/bash
OBJECT_NAME=
OAUTH2)TOKEN=
OBJECT_CONTENT_TYPE=
BUCKET_NAME=
GCLOUD_REGION=

gsutil signurl -r ${GCLOUD_REGION} -c 'text/plain' -m PUT serviceAccount.json gs://${BUCKET_NAME}

curl -X PUT --data-binary @OBJECT_LOCATION \
-H "Authorization: Bearer OAUTH2_TOKEN" \
-H "Content-Type: OBJECT_CONTENT_TYPE" \
"https://storage.googleapis.com/BUCKET_NAME/OBJECT_NAME"

