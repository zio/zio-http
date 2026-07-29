import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ClaudeLogo, CodexLogo, CursorLogo, OpenCodeLogo } from './logos';

export const PROMPT =
  'Fetch https://ziohttp.com/start.md and follow the instructions to set up my environment for ZIO HTTP development.';

export async function copyPrompt(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (_) {
    // fall through to legacy path
  }
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch (_) {
    return false;
  }
}

export default function OnboardAgentButton({ tone = 'default' }) {
  const [copied, setCopied] = useState(false);
  const timer = useRef(null);

  useEffect(() => () => timer.current && clearTimeout(timer.current), []);

  const onClick = useCallback(async () => {
    const ok = await copyPrompt(PROMPT);
    if (!ok) {
      window.prompt('Copy this prompt for your coding agent:', PROMPT);
      return;
    }
    setCopied(true);
    timer.current && clearTimeout(timer.current);
    timer.current = setTimeout(() => setCopied(false), 2000);
  }, []);

  const toneClasses =
    tone === 'onDark'
      ? 'border-white bg-black/40 text-white backdrop-blur-sm hover:border-primary hover:text-primary'
      : 'border-zinc-400 text-zinc-800 hover:border-primary hover:text-primary dark:border-zinc-600 dark:text-zinc-100';

  return (
    <button
      type="button"
      onClick={onClick}
      title="Copy the prompt to onboard your coding agent to ZIO HTTP"
      aria-label="Copy the ZIO HTTP agent onboarding prompt to the clipboard"
      className={`flex items-center gap-2 rounded-full border-2 px-6 py-2.5 text-base font-semibold leading-normal transition-colors ${toneClasses}`}
    >
      <span>{copied ? 'Copied!' : 'Onboard your agent to ZIO HTTP'}</span>
      <span className="flex items-center gap-1" aria-hidden="true">
        <ClaudeLogo />
        <CodexLogo />
        <CursorLogo />
        <OpenCodeLogo />
      </span>
      <span className="sr-only">
        Works with Claude, Codex, Cursor, and OpenCode
      </span>
    </button>
  );
}
