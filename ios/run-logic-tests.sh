#!/bin/sh
# Compiles the iOS logic files plus LogicTests/main.swift for the Mac (no simulator) and runs them.
set -e
cd "$(dirname "$0")"
out="${TMPDIR:-/tmp}/plansaver-logic-tests"
swiftc -o "$out" PlanSaver/Plan.swift PlanSaver/PlanData.swift PlanSaver/PlanRepository.swift PlanSaver/Phones.swift PlanSaver/Catalog.swift LogicTests/main.swift
"$out"
