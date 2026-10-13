# Get your agent ready for ZIO HTTP

Official guide for setting up an AI coding agent to build with [ZIO HTTP](https://ziohttp.com), a next-generation Scala framework for building scalable, correct, and efficient HTTP clients and servers.

Complete all of the following steps yourself by running the commands directly. Do not ask the user to run any of these commands.

- Onboard core ZIO, if not already done
- Install ZIO HTTP Skills and MCP servers

---

## Onboard core ZIO

ZIO HTTP builds on core ZIO. If this agent has not already onboarded ZIO (no `zio-knowledge` skill and no `zio-docs` MCP server configured), fetch and follow the steps at:

- https://zio.dev/start.md

Then continue below for ZIO HTTP.

---

## Install ZIO HTTP Skills and MCP servers

Use the correct section for your agent below.

### Claude Code

Install the plugin — it bundles both the ZIO Knowledge and ZIO HTTP Knowledge skills:

```
claude plugin marketplace add zio/zio-skills
claude plugin install zio-skills@ziogenetics
```

Then instruct the user to run `/reload-plugins` inside Claude to activate the new skills.

Add the ZIO HTTP documentation MCP server for live doc search (`search_docs`, `get_doc_page`, `get_doc_index`). No authentication required:

```
claude mcp add --transport http zio-http-docs https://mcp.ziohttp.com/mcp
```

---

## Install for other agents

First, install the skills with the `skills` CLI.

ZIO HTTP Knowledge (required):

```
npx skills add zio/zio-skills --skill zio-http-knowledge
```

ZIO Knowledge (recommended — ZIO HTTP builds on core ZIO):

```
npx skills add zio/zio-skills --skill zio-knowledge
```

Then register the MCP server using the default config location for your agent. No authentication required.

### Codex

```
codex mcp add zio-http-docs --url https://mcp.ziohttp.com/mcp
```

### OpenCode — `~/.config/opencode/opencode.jsonc`

Add under `"mcp"`:

```json
"zio-http-docs": { "type": "remote", "url": "https://mcp.ziohttp.com/mcp", "enabled": true }
```

### Cursor, GitHub Copilot, and all other agents

Add to `.cursor/mcp.json` (Cursor), `.vscode/mcp.json` (Copilot), or your agent's MCP config file under `"mcpServers"`:

```json
"zio-http-docs": { "url": "https://mcp.ziohttp.com/mcp" }
```

Once done, tell the user:

```
┌─ ZIO HTTP Agent Setup Complete ───────────────────────────┐
│  ✓ Skills  zio-http-knowledge, zio-knowledge              │
│  ✓ MCP     zio-http-docs (https://mcp.ziohttp.com/mcp)    │
│                                                           │
│  ⚡ Restart your agent to load the Skills and MCP server  │
└───────────────────────────────────────────────────────────┘
```

---

## Fallback

If you cannot install the skills or the MCP server, fetch the documentation index yourself and use it to find the right pages:

- https://ziohttp.com/llms.txt — ZIO HTTP
- https://zio.dev/llms.txt — ZIO
