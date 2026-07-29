import React, { useEffect, useState } from 'react';
import { repoStats } from './data';

function formatStars(n) {
  if (n >= 1000) {
    return `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k`;
  }
  return `${n}`;
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

  const items = [
    { value: formatStars(stars), label: 'stars' },
    { value: repoStats.contributors, label: 'contributors' },
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
