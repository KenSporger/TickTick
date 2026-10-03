#!/bin/bash
WORKFLOW_STATE=".claude/workflow/workflow-state.json"
INPUT=$(cat /dev/stdin 2>/dev/null || echo "{}")
STOP_ACTIVE=$(echo "$INPUT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('stop_hook_active',False))" 2>/dev/null || echo "False")
if [ "$STOP_ACTIVE" = "True" ]; then exit 0; fi
if [ -f "$WORKFLOW_STATE" ]; then
  PHASE=$(python3 -c "import json; print(json.load(open('$WORKFLOW_STATE')).get('phase',''))" 2>/dev/null || echo "")
  if [ "$PHASE" != "" ] && [ "$PHASE" != "completed" ] && [ "$PHASE" != "stopped" ]; then
    echo "{\"decision\":\"block\",\"reason\":\"[auto-dev] Phase $PHASE not complete. Continue from workflow state.\"}"
    exit 2
  fi
fi
exit 0

