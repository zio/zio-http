import React, { useCallback, useEffect, useRef, useState } from 'react';
import { PROMPT } from './prompt.mjs';
import styles from './styles.module.css';

export { PROMPT };

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

// Hero card: copies the same setup prompt as the navbar button.
export default function OnboardAgentButton() {
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

  return (
    <button
      type="button"
      onClick={onClick}
      title="Copy the prompt to onboard your coding agent to ZIO HTTP"
      aria-label="Copy the ZIO HTTP agent onboarding prompt to the clipboard"
      className={styles.card}
    >
      <span className={styles.title}>
        {copied ? 'Copied!' : 'Onboard your agent to ZIO HTTP'}
      </span>
      <span className={styles.arrow} aria-hidden="true">
        &rarr;
      </span>
      <span className="sr-only">
        Works with Claude, Codex, Cursor, and OpenCode
      </span>
    </button>
  );
}
