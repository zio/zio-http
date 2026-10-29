import React from 'react';
import Link from '@docusaurus/Link';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';
import OnboardAgentButton from '@site/src/components/OnboardAgentButton';
import RepoStats from '@site/src/components/RepoStats';

const featureTags = ['Ultra-Fast', 'Type-Safe', 'Modular'];

const heroCode = `val routes = Routes(
  Method.GET / "greet" -> handler { (req: Request) =>
    val name = req.queryParamOrElse("name", "World")
    Response.text(s"Hello, $name!")
  }
)`;

export default function HomepageHero() {
  return (
    <section className={`hp-inner ${styles.hero}`}>
      {/* The page reads as an HTTP exchange: request arrow in, response arrow out. */}
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
              className="hp-button hp-button--primary"
              to="/installation"
              title="View the ZIO HTTP installation guide">
              Get Started
            </Link>
            <a
              className="hp-button"
              href="https://github.com/zio/zio-http"
              target="_blank"
              rel="noopener noreferrer"
              title="Star ZIO HTTP on GitHub">
              Star on GitHub
            </a>
            <RepoStats />
          </div>
        </div>

        <div className={styles.side}>
          <div className={`hp-code ${styles.codeCard}`}>
            <div className={styles.codeHeader}>
              <span className={styles.fileName}>GreetingServer.scala</span>
              <span className={styles.language}>SCALA 3</span>
            </div>
            <CodeBlock language="scala">{heroCode}</CodeBlock>
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
    </section>
  );
}
