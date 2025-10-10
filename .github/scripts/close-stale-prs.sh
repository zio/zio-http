#!/bin/bash
set -e

# Close PRs without successful build after 3 days
# Usage: ./close-stale-prs.sh

THREE_DAYS_SECONDS=$((3 * 24 * 60 * 60))
NOW=$(date +%s)

echo "Checking for stale PRs without successful builds..."

# Get all open PRs
gh pr list --state open --json number,isDraft,updatedAt,headRefOid --limit 100 | jq -c '.[]' | while read -r pr; do
  PR_NUMBER=$(echo "$pr" | jq -r '.number')
  IS_DRAFT=$(echo "$pr" | jq -r '.isDraft')
  UPDATED_AT=$(echo "$pr" | jq -r '.updatedAt')
  HEAD_SHA=$(echo "$pr" | jq -r '.headRefOid')

  # Skip draft PRs
  if [ "$IS_DRAFT" = "true" ]; then
    echo "Skipping draft PR #$PR_NUMBER"
    continue
  fi

  # Calculate age
  UPDATED_TIMESTAMP=$(date -j -f "%Y-%m-%dT%H:%M:%SZ" "$UPDATED_AT" +%s 2>/dev/null || date -d "$UPDATED_AT" +%s 2>/dev/null || echo "0")
  PR_AGE=$((NOW - UPDATED_TIMESTAMP))
  PR_AGE_DAYS=$((PR_AGE / 86400))

  # Only check PRs older than 3 days
  if [ "$PR_AGE" -lt "$THREE_DAYS_SECONDS" ]; then
    echo "PR #$PR_NUMBER is too recent (updated $PR_AGE_DAYS days ago)"
    continue
  fi

  echo "Checking PR #$PR_NUMBER (updated $PR_AGE_DAYS days ago)"

  # Check CI status for the latest commit
  # Get check runs
  CHECK_RUNS=$(gh api "repos/{owner}/{repo}/commits/$HEAD_SHA/check-runs" --jq '.check_runs')
  CHECK_RUNS_COUNT=$(echo "$CHECK_RUNS" | jq 'length')

  # Get commit statuses
  COMMIT_STATUS=$(gh api "repos/{owner}/{repo}/commits/$HEAD_SHA/status" --jq '.state')
  STATUSES_COUNT=$(gh api "repos/{owner}/{repo}/commits/$HEAD_SHA/status" --jq '.statuses | length')

  # If no CI runs at all, skip
  if [ "$CHECK_RUNS_COUNT" -eq 0 ] && [ "$STATUSES_COUNT" -eq 0 ]; then
    echo "PR #$PR_NUMBER has no CI runs, skipping"
    continue
  fi

  # Check if there's at least one successful build
  HAS_SUCCESSFUL_CHECK=$(echo "$CHECK_RUNS" | jq 'any(.[]; .conclusion == "success")')
  HAS_SUCCESSFUL_STATUS=false
  if [ "$COMMIT_STATUS" = "success" ]; then
    HAS_SUCCESSFUL_STATUS=true
  fi

  if [ "$HAS_SUCCESSFUL_CHECK" = "true" ] || [ "$HAS_SUCCESSFUL_STATUS" = "true" ]; then
    echo "PR #$PR_NUMBER has a successful build, keeping open"
    continue
  fi

  echo "PR #$PR_NUMBER has no successful build, closing..."

  # Post comment explaining why we're closing
  gh pr comment "$PR_NUMBER" --body "👋 This PR is being automatically closed because it hasn't had a successful CI build in over 3 days.

If you're still working on this, please:
1. Fix any failing tests or build issues
2. Push your changes
3. Reopen this PR once the build passes

Thank you for your contribution! 🙏"

  # Close the PR
  gh pr close "$PR_NUMBER"

  echo "Closed PR #$PR_NUMBER"
done

echo "Done checking PRs"

