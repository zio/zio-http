# ZIO-HTTP(zHttp) Release Cadence

- [Pre-Release cycles](#pre-release-cycles)
  - [Release Highlights](#release-highlights)
  - [Release Process](#release-process)
  - [A Release Example](#a-release-example)

- [Versioning](#versioning)
- [Change log Templates](#change-log-templates)
  - [Guiding Principle](#guiding-principle)
  - [Example of a Changelog](#example-of-a-changelog)

# Pre-Release cycles

### Release Highlights
* ZIO Http will follow a fortnightly release before first major release
* We aim for regular and strict one release per 2 weeks.
* 2 weeks are counted from the first release candidate to another.
* This means that there is no code freeze and no feature should block a release

### Release Process
* We will target releases every second Wednesday. This will give us time to find some major bugs and do a patch release if required in the next 2-3 days
* From main we will cut a branch following this pattern
``` 
v<major>.<minor+1>.0-RC<+1>
Example:  v1.1.0-RC1   (if previous release was v1.0.x)
```
* All our PRs meant for this release (including mainly stability and bug fixes) are based on this branch. (Remember no new features are included in this branch)
* Making sure documentation/examples are updated by authors.

### A Release Example
| **Release**    | **Date**         |
| ----------- | ----------- |
| 1.0.0.0-RC1       | Wed Dec 8, 2021       |
| 1.0.0   | Wed Dec 23, 2021        |
| 1.0.1 (Major bug was found in 1.0)| Fri Dec 24, 2021|

* The above table is a rough example. In reality, the dates may change due to holidays, availability of resources, etc.
* The patch fixes (0.1.1) are very important and the triage may take more than 2-3 days depending on the complexity of the bug.
* Major versions (with possible breaking changes) can be released in 6 months or a year.
* The creation of a release candidate can be done earlier (for example Monday, Dec 6)  based on the team discussion

# Versioning
* ZIO Http will follow semantic versioning
* Every pre-release will be versioned: <MAJOR>.<FEATURE>.<PATCH>-<RC | M>
* ***MAJOR***: Major releases are not on any particular cadence. Major releases are likely to be stable over a long period (one year or more)
* **FEATURE**: ZIO Http is targeting new feature releases every fortnightly and will include new features, improvements, and bug fixes
* **PATCH**: This is reserved for urgent bug fixes on the current release
* **RC (Release Candidate)**: This is reserved for beta=version
* **M (Milestone)**: This is reserved for major Milestone before every prerelease

# Backward compatibility
We will not support backward compatibility till a major stable release

# Change Log templates
### Guiding principle

Changelog will be based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
* There should be a single changelog for one version
* Similar types of changes should be grouped.
* Every changelog should have the following types (with Github links to pull requests for each change)
* Added for new features.
* Changed for changes in existing functionality.
* Deprecated for soon-to-be removed features.
* Removed for now removed features.
* Fixed for any bug fixes.
* Security in case of vulnerabilities

### Example of a Changelog
The example below shows a sample structure of a Changelog being built on top of the previous changelogs (current changes on top followed by descending dates)
```
## Unreleased (Can refer to added/fixed/changed etc of a current release candidate)

### Added

- [#393](https://github.com/dream11/zio-http/pull/393) finalFragment flag for WebSocketFrame
- [#485](https://github.com/dream11/zio-http/pull/485) ifThenElse method in HttpMiddleware
- [#277](https://github.com/dream11/zio-http/pull/277) Cookie Support

### Fixed

- [#387](https://github.com/dream11/zio-http/pull/387) HttpApp write response only once
- [#323](https://github.com/dream11/zio-http/pull/323) Route matching with encoded paths
- [#314](https://github.com/dream11/zio-http/pull/314) Added timeout and flaky in test

### Changed

- [#541](https://github.com/dream11/zio-http/pull/541) Use Option instead of Either in Cookies
- [#577](https://github.com/dream11/zio-http/pull/577) Add toApp, toResponse methods to status

## [1.0.0] - 2021-12-22 (link to the previous release if any)

### Fixed

- [#387](https://github.com/dream11/zio-http/pull/387) HttpApp write response only once
- [#323](https://github.com/dream11/zio-http/pull/323) Route matching with encoded paths
- [#314](https://github.com/dream11/zio-http/pull/314) Added timeout and flaky in test

### Changed

- [#541](https://github.com/dream11/zio-http/pull/541) Use Option instead of Either in Cookies
- [#577](https://github.com/dream11/zio-http/pull/577) Add toApp, toResponse methods to status
```



