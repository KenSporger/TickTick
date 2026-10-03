#!/bin/bash
WORKFLOW_DIR=".claude/workflow"
WORKFLOW_STATE="$WORKFLOW_DIR/workflow-state.json"
if [ ! -f "$WORKFLOW_STATE" ]; then exit 0; fi
PHASE=$(python3 -c "import json; print(json.load(open('$WORKFLOW_STATE')).get('phase',''))")
if [ "$PHASE" = "" ] || [ "$PHASE" = "completed" ] || [ "$PHASE" = "stopped" ]; then exit 0; fi
echo "=== [auto-dev] Workflow Resume ==="
if [ -f "$WORKFLOW_DIR/compact-summary.md" ]; then cat "$WORKFLOW_DIR/compact-summary.md"; else echo "Current Phase: $PHASE"; fi
echo "Read workflow-state.json, task-pool.json, phase instructions, safety-rules.md and fallbacks.md."

