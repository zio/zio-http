import React from 'react';
import clsx from 'clsx';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

export default function HomepageCodingAgent() {
  const installCommand = `npx skills add zio/zio-skills --skill zio-http-knowledge`;

  return (
    <section className={styles.codingAgent}>
      <div className={styles.wideContainer}>
        <div className={styles.singleColumn}>
          <div className={styles.agentContent}>
            <h2 className={clsx("sectionHeader", "text-4xl", "text-center")}>Teach Your Coding Agent Latest ZIO HTTP Knowledge</h2>
            <p>
              The <code>zio-http-knowledge</code> skill teaches your coding agent to fetch live documentation
              from zio-http docs before answering any ZIO HTTP question — so you always get accurate, up-to-date
              answers, not guesses from stale training data.
            </p>
            <ul>
              <li>Covers ZIO HTTP core, routing, middleware, streaming, and more</li>
              <li>Fetches current related docs from zio-http on ZIO HTTP related development questions</li>
            </ul>
          </div>
          <div className={styles.codeContainer}>
            <div className={styles.codeWrapper}>
              <CodeBlock language="bash">
                {installCommand}
              </CodeBlock>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
