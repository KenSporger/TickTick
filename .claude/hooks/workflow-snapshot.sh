#!/bin/bash
WORKFLOW_DIR=".claude/workflow"
WORKFLOW_STATE="$WORKFLOW_DIR/workflow-state.json"
if [ ! -f "$WORKFLOW_STATE" ]; then exit 0; fi
PHASE=$(python3 -c "import json; print(json.load(open('$WORKFLOW_STATE')).get('phase','unknown'))")
DONE=$(python3 -c "import json; print(json.load(open('$WORKFLOW_STATE')).get('tasks_done',0))")
TOTAL=$(python3 -c "import json; print(json.load(open('$WORKFLOW_STATE')).get('tasks_total',0))")
CODER=$(python3 -c "import json; print(json.load(open('$WORKFLOW_STATE')).get('config',{}).get('coder','unknown'))")
printf '# Auto-Dev Workflow State\n- Phase: %s\n- Progress: %s/%s\n- Coder: %s\n' "$PHASE" "$DONE" "$TOTAL" "$CODER" > "$WORKFLOW_DIR/compact-summary.md"

