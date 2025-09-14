# PR Maintenance Automation

This document describes the PR maintenance automation implemented for the zio-http repository.

## Overview

The PR maintenance automation (`.github/workflows/pr-maintenance.yml`) implements two key features requested in issue #3675:

1. **Fixes Reference Reminder**: Automatically reminds PR authors to include issue references
2. **Stale PR Cleanup**: Automatically closes PRs that haven't had successful builds for 5 days

## Features

### 1. Fixes Reference Reminder

**Trigger**: When a PR is opened, edited, or synchronized  
**Condition**: PR is not a draft  
**Action**: Checks if PR title or description contains a "fixes #XXX" pattern

The automation looks for these patterns (case-insensitive):
- `fixes #123`
- `closes #456`  
- `resolves #789`

If no such pattern is found, it posts a helpful comment reminding the author.

### 2. Stale PR Cleanup

**Trigger**: Daily at 12:00 UTC (via scheduled cron job)  
**Condition**: PR is older than 5 days and has no successful builds  
**Action**: Posts a comment and closes the PR

The automation:
- Skips draft PRs completely
- Only considers PRs updated more than 5 days ago
- Checks for successful "Build and Test" check runs or "Continuous Integration" workflow runs
- Posts a polite comment before closing
- Closes the PR automatically

## Implementation Details

### Permissions Required
- `contents: read` - To read repository content
- `pull-requests: write` - To comment on and close PRs  
- `issues: write` - To create comments (PRs are issues in GitHub API)
- `actions: read` - To check workflow run statuses

### Integration with Existing CI

The automation integrates seamlessly with the existing CI setup:
- References the "Continuous Integration" workflow by name
- Checks for successful "Build and Test" jobs
- Respects the existing automerge workflow

### Rate Limiting & Performance

- The stale PR cleanup runs only once daily
- Uses efficient GitHub API calls
- Processes PRs in batches to avoid rate limits
- Skips unnecessary checks when possible

## Configuration

### Adjusting Time Thresholds

To change the 5-day threshold, modify the `fiveDaysAgo` calculation in the workflow:

```javascript
const fiveDaysAgo = new Date(Date.now() - 5 * 24 * 60 * 60 * 1000);
```

### Modifying the Reminder Message

The reminder message can be customized in the `check-pr-format` job's script section.

### Changing Schedule

The cleanup schedule can be modified in the `schedule` trigger:

```yaml
schedule:
  - cron: '0 12 * * *'  # Daily at 12:00 UTC
```

## Testing

The automation includes pattern testing for the "fixes" reference detection. The regex patterns are thoroughly tested to ensure they match the expected GitHub linking patterns while avoiding false positives.

## Troubleshooting

### Common Issues

1. **Permissions errors**: Ensure the workflow has the required permissions listed above
2. **API rate limits**: The daily schedule helps avoid rate limits, but high PR volumes might require adjustment
3. **False positives**: The "fixes" pattern is designed to be strict to avoid false matches

### Monitoring

Monitor the automation by:
- Checking the Actions tab for workflow run logs
- Observing PR comments for the reminder messages
- Tracking closed PRs with the automation comment

## Future Enhancements

Potential improvements that could be added:
- Configurable time thresholds via repository variables
- Different thresholds for different types of PRs
- Integration with specific labels or PR templates
- Metrics collection for monitoring automation effectiveness