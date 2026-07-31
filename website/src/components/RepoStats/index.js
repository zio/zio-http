import React, { useEffect, useRef, useState } from 'react';
import { repoStats } from './data';

function formatStars(n) {
  if (n >= 1000) {
    return `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k`;
  }
  return `${n}`;
}

// Animate an integer from its previous displayed value up to `target` with an
// ease-out curve. Runs whenever `target` changes (initial mount 0 → value, and
// again if the live GitHub count arrives). Reduced-motion / no-rAF snaps.
function useCountUp(target, duration = 1200) {
  const [value, setValue] = useState(0);
  const fromRef = useRef(0);

  useEffect(() => {
    const reduce =
      typeof window !== 'undefined' &&
      window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce || typeof requestAnimationFrame === 'undefined') {
      setValue(target);
      fromRef.current = target;
      return undefined;
    }
    const from = fromRef.current;
    const start = performance.now();
    let raf;
    const ease = (t) => 1 - Math.pow(1 - t, 3);
    const step = (now) => {
      const t = Math.min(1, (now - start) / duration);
      setValue(Math.round(from + (target - from) * ease(t)));
      if (t < 1) {
        raf = requestAnimationFrame(step);
      } else {
        fromRef.current = target;
      }
    };
    raf = requestAnimationFrame(step);
    return () => raf && cancelAnimationFrame(raf);
  }, [target, duration]);

  return value;
}

/**
 * Hero trust labels: live star count and latest release version (static
 * contributor count). The GitHub calls are unauthenticated and best-effort —
 * any failure (offline, rate limit) silently keeps the baked-in fallbacks.
 * Styled for the always-dark hero banner.
 */
export default function RepoStats() {
  const [stars, setStars] = useState(repoStats.stars);
  const [version, setVersion] = useState(repoStats.version);

  useEffect(() => {
    let active = true;
    const base = `https://api.github.com/repos/${repoStats.owner}/${repoStats.repo}`;

    fetch(base)
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (active && data && typeof data.stargazers_count === 'number') {
          setStars(data.stargazers_count);
        }
      })
      .catch(() => {
        /* keep fallback */
      });

    fetch(`${base}/releases/latest`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (active && data && data.tag_name) {
          setVersion(data.tag_name);
        }
      })
      .catch(() => {
        /* keep fallback */
      });

    return () => {
      active = false;
    };
  }, []);

  // Count-up: stars (numeric) and contributors (parse "150+" → 150, re-append
  // the non-digit suffix). Version stays static (non-numeric).
  const contributorsTarget = parseInt(repoStats.contributors, 10) || 0;
  const contributorsSuffix = String(repoStats.contributors).replace(/[0-9]/g, '');
  const starsCount = useCountUp(stars);
  const contributorsCount = useCountUp(contributorsTarget);

  const items = [
    { value: formatStars(starsCount), label: 'stars' },
    { value: `${contributorsCount}${contributorsSuffix}`, label: 'contributors' },
    { value: version, label: 'latest' },
  ];

  return (
    <div className="mt-2 flex flex-wrap items-center justify-start gap-x-6 gap-y-1 text-sm text-zinc-300">
      {items.map((item) => (
        <span key={item.label}>
          <span className="font-bold text-white">{item.value}</span>{' '}
          {item.label}
        </span>
      ))}
    </div>
  );
}
