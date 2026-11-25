#!/bin/bash
set -e

# Check if PR has issue reference in title or body
# Usage: ./check-issue-reference.sh <pr-number>

PR_NUMBER=$1

if [ -z "$PR_NUMBER" ]; then
  echo "Error: PR number is required"
  exit 1
fi

# Get PR details
PR_DATA=$(gh pr view "$PR_NUMBER" --json title,body,isDraft)

IS_DRAFT=$(echo "$PR_DATA" | jq -r '.isDraft')
if [ "$IS_DRAFT" = "true" ]; then
  echo "Skipping draft PR"
  exit 0
fi

PR_TITLE=$(echo "$PR_DATA" | jq -r '.title')
PR_BODY=$(echo "$PR_DATA" | jq -r '.body // ""')
COMBINED_TEXT="$PR_TITLE"$'\n'"$PR_BODY"

# Check for issue reference patterns (fixes #123, closes #123, resolves #123, etc.)
if echo "$COMBINED_TEXT" | grep -qiE '\b(close|closes|closed|fix|fixes|fixed|resolve|resolves|resolved)\s+([a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+)?#[0-9]+\b'; then
  echo "✅ Issue reference found in PR"
  exit 0
fi

echo "⚠️  No issue reference found"

# Check if we already commented
EXISTING_COMMENT=$(gh pr view "$PR_NUMBER" --json comments --jq '.comments[] | select(.body | contains("missing an issue reference")) | .id' | head -n 1)

if [ -n "$EXISTING_COMMENT" ]; then
  echo "Reminder comment already exists"
  exit 0
fi

# Post reminder comment
gh pr comment "$PR_NUMBER" --body "👋 Hi! This PR appears to be missing an issue reference in the title or description.

Please link to the issue this PR addresses using one of these formats:
- \`fixes #123\`
- \`closes #123\`
- \`resolves #123\`

This helps us track which issues are being worked on and automatically closes them when the PR is merged.

If this PR doesn't fix a specific issue, you can ignore this message. 😊"

echo "Posted reminder comment about missing issue reference"

