# Get your agent ready for ZIO HTTP

Official guide for setting up an AI coding agent to build with [ZIO HTTP](https://ziohttp.com), a next-generation Scala framework for building scalable, correct, and efficient HTTP clients and servers.

Install the ZIO skills below so your agent answers from live, accurate ZIO HTTP documentation instead of stale training data.

## Claude Code

Install the plugin — it bundles both the ZIO Knowledge and ZIO HTTP Knowledge skills:

```
claude plugin marketplace add zio/zio-skills
claude plugin install zio-skills@ziogenetics
```

Then reload plugins with the `/reload-plugins` command to activate the skills.

## Other agents (Codex, OpenCode, Cursor, etc.)

Install the skills with the `skills` CLI.

ZIO HTTP Knowledge (required):

```
npx skills add zio/zio-skills --skill zio-http-knowledge
```

ZIO Knowledge (recommended — ZIO HTTP builds on core ZIO):

```
npx skills add zio/zio-skills --skill zio-knowledge
```

## Fallback

If you cannot install the skills, fetch the documentation index yourself and use it to find the right pages:

- https://ziohttp.com/llms.txt — ZIO HTTP
- https://zio.dev/llms.txt — ZIO
