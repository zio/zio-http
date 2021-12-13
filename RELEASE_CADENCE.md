# ZIO-HTTP(zHttp) Release Cadence

- [Pre-Release cycles](#pre-release-cycles)
    - [Release Highlights](#release-highlights)
    - [Release Process](#release-process)
    - [A Release Example](#a-release-example)

- [Versioning](#versioning)
- [Change log Templates](#change-log-templates-wip---example-will-be-added)

# Pre-Release cycles

### Release Highlights
* zHttp will follow a fortnightly release before first major release
* We aim for regular and strict one release per 2 weeks.
* 2 weeks is counter from first release candidate to another.
* Th is means that there is no code freeze and no feature should block a release

### Release Process
* We will target releases every second Wednesday. This will give us time to find some major bug and do a patch release if required in the next 2-3 days
* From release/next we will cut a branch following this pattern 	
```  
 v<major>.<minor+1>.0-RC<+1>
 Example:  v1.1.0-RC1   (if previous release was v1.0.x) 
```
* All our PRs meant for this release (including mainly stability and bug fixes) are based against this branch. (Remember no new features are included in this branch)
* The captain will use this vX.Y.Z-rc branch to create a small PR to the main branch and update the CHANGELOG.md on the release day and update version where required.
* Make sure documentation / examples are updated by authors.

### A Release Example
| **Release**    | **Date**         |
| ----------- | ----------- |
| 1.0.0.0-RC1       | Wed Dec 8, 2021       |
| 1.0   | Wed Dec 23, 2021        |
| 1.0.1 (Major bug was found in 1.0)| Fri Dec 24, 2021|

* The above table is a rough example. In reality the dates may change due to holidays, availability of resources etc.
* The patch fixes (0.1.1) are very important and the triage may take more than 2-3 days depending on the complexity of the bug.
* Major versions (with possible breaking changes) can be released in 6 months or a year.
* The creation of a release candidate can be done earlier (example Monday Dec 6)  based on the team discussion

# Versioning
* zHttp will follow semantic versioning
* Every pre-release will be versioned: <MAJOR>.<FEATURE>.<PATCH>-<RC | M>
* ***MAJOR***: Major releases are not on any particular cadence. Major releases are likely to be stable over a long period (one year or more)
* **FEATURE**: zHttp are targeting new feature releases every fortnightly and will include new features, improvements and bug fixes
* **PATCH**: This is reserved for urgent bug fixes on the current release
* **RC(Release Candidate)**: This is reserved for beta version
* **M(Milestone)**: This is reserved for major Milestone before every prerelease

# Backward compatibility
We will not support backward compatibility till major stable release

# Change Log templates (WIP - example will be added)
### Guiding principle

Changelog will be based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
* There should be single changelog for one version
* Similar types of changes should be grouped together.
* Every changelog should have following types (with github links to commit ids for each change)
  * Added for new features.
  * Changed for changes in existing functionality.
  * Deprecated for soon-to-be removed features.
  * Removed for now removed features.
  * Fixed for any bug fixes.
  * Security in case of vulnerabilities

