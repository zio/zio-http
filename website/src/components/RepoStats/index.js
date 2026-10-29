import React, { useEffect, useState } from 'react';
import { repoStats } from './data';
import styles from './styles.module.css';

/**
 * Hero version label: the latest release tag from GitHub. The call is
 * unauthenticated and best-effort: any failure (offline, rate limit)
 * silently keeps the baked-in fallback.
 */
export default function RepoStats() {
  const [version, setVersion] = useState(repoStats.version);

  useEffect(() => {
    let active = true;
    const url = `https://api.github.com/repos/${repoStats.owner}/${repoStats.repo}/releases/latest`;

    fetch(url)
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

  return <span className={styles.version}>{version} latest</span>;
}
