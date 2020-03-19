#!/bin/bash

function backlogStat() {
  echo `date --iso-8601=seconds`
}

while true; do backlogStat; sleep 1; done
