import React from 'react';
import Link from '@docusaurus/Link';
import styles from './styles.module.css';
import OnboardAgentButton from '@site/src/components/OnboardAgentButton';
import RepoStats from '@site/src/components/RepoStats';

const featureTags = ['Ultra-Fast', 'Type-Safe', 'Modular'];

export default function HomepageHero() {
  return (
    <header className={styles.hero}>
      <div className={styles.inner}>
        {/* The hero reads as an HTTP exchange: request arrow in, response arrow out. */}
        <div className={styles.request} aria-hidden="true">
          <span className={styles.arrowLabel}>REQUEST</span>
          <div className={styles.arrowRow}>
            <div className={styles.arrowBar} />
            <svg className={styles.arrowHeadRight} width="26" height="44" viewBox="0 0 26 44" fill="none" stroke="currentColor" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round">
              <path d="M4 4L22 22L4 40" />
            </svg>
          </div>
        </div>

        <div className={styles.grid}>
          <div className={styles.copy}>
            <h1 className={styles.heading}>
              Type-safe, High-performance HTTP for Scala,{' '}
              <span className={styles.accent}>Powered by ZIO</span>
            </h1>
            <p className={styles.subtitle}>
              Explore the most performant, type-safe HTTP library for ZIO. Built
              for modern microservices.
            </p>
            <ul className={styles.tags}>
              {featureTags.map((label) => (
                <li key={label} className={styles.tag}>{label}</li>
              ))}
            </ul>
            <div className={styles.actions}>
              <Link
                className={styles.primary}
                to="/installation"
                title="View the ZIO HTTP installation guide">
                Get Started &rarr;
              </Link>
              <a
                className={styles.secondary}
                href="https://github.com/zio/zio-http"
                target="_blank"
                rel="noopener noreferrer"
                title="Star ZIO HTTP on GitHub">
                Star on GitHub
              </a>
            </div>
            <RepoStats />
          </div>

          <div className={styles.side}>
            <div className={styles.codeCard}>
              <div className={styles.codeHeader}>
                <span className={styles.fileName}>GreetingServer.scala</span>
                <span className={styles.language}>SCALA 3</span>
              </div>
              <pre className={styles.code}>
                <span className={styles.kw}>val</span> routes = <span className={styles.type}>Routes</span>(
{'\n  '}<span className={styles.type}>Method</span>.GET / <span className={styles.str}>"greet"</span> -&gt; handler {'{'} (req: <span className={styles.type}>Request</span>) =&gt;
{'\n    '}<span className={styles.kw}>val</span> name = req.queryParamOrElse(<span className={styles.str}>"name"</span>, <span className={styles.str}>"World"</span>)
{'\n    '}<span className={styles.type}>Response</span>.text(<span className={styles.str}>{'s"Hello, $name!"'}</span>)
{'\n  }\n)'}
              </pre>
            </div>
            <OnboardAgentButton />
          </div>
        </div>

        <div className={styles.response} aria-hidden="true">
          <div className={styles.arrowRow}>
            <svg className={styles.arrowHeadLeft} width="26" height="44" viewBox="0 0 26 44" fill="none" stroke="currentColor" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 4L4 22L22 40" />
            </svg>
            <div className={styles.arrowBar} />
          </div>
          <span className={styles.arrowLabel}>RESPONSE &middot; 200 OK</span>
        </div>
      </div>
    </header>
  );
}
