#!/bin/bash

# Get a specific version of ktlint
curl -sSLO https://github.com/pinterest/ktlint/releases/download/1.8.0/ktlint
chmod a+x ktlint

# Use it to fix as many problems as it can
./ktlint -F "!**/build/**"
