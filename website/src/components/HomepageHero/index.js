import React from 'react';
import Link from '@docusaurus/Link';
import { FaRocket, FaShieldHalved, FaCubes, FaArrowRight, FaGithub } from 'react-icons/fa6';
import styles from './styles.module.css';
import OnboardAgentButton from '@site/src/components/OnboardAgentButton';
import RepoStats from '@site/src/components/RepoStats';

const featureTags = [
  { icon: FaRocket, label: 'Ultra-Fast' },
  { icon: FaShieldHalved, label: 'Type-Safe' },
  { icon: FaCubes, label: 'Modular' },
];

export default function HomepageHero() {
  return (
    <header className={styles.heroBanner}>
      <div className={styles.overlay} />
      <div className={styles.contentGrid}>
        {/* Left Column (desktop) / Top section (mobile): Banner + ZIO HTTP */}
        <div className={styles.leftColumn}>
          <h2 className={styles.zioTitle}>ZIO HTTP</h2>
        </div>

        {/* Right Column (desktop) / Bottom section (mobile): Copy + Features */}
        <div className={styles.rightColumn}>
          <h1 className={styles.mainHeading}>
            <span className={styles.headingLine}>POWERING</span>
            <span className={styles.headingLine}>HIGH-PERFORMANCE</span>
            <span className={styles.headingLine}>FUNCTIONAL APIs</span>
          </h1>
          <p className={styles.subtitle}>
            Explore the most performant, type-safe HTTP library for ZIO.
            Built for modern microservices.
          </p>
          <div className={styles.featureTags}>
            {featureTags.map(({ icon: Icon, label }) => (
              <div key={label} className={styles.featureTag}>
                <Icon className={styles.featureIcon} size={20} aria-hidden="true" />
                <span>{label}</span>
              </div>
            ))}
          </div>
          <div className={styles.heroCta}>
            <div className={styles.onboardRow}>
              <OnboardAgentButton tone="onDark" />
            </div>
            <div className={styles.buttons}>
              <Link
                className="button button--secondary button--lg"
                to="/installation"
                title="View the ZIO HTTP installation guide">
                <span>Get Started</span>
                <span> </span>
                <FaArrowRight className={styles.arrowIcon} />
              </Link>
              <a
                href="https://github.com/zio/zio-http"
                target="_blank"
                rel="noopener noreferrer"
                title="Star ZIO HTTP on GitHub"
                className="hover:border-primary hover:text-primary flex items-center justify-center gap-2 rounded-full border-2 border-white bg-black/40 px-7 py-3.5 text-base font-semibold text-white backdrop-blur-sm transition-colors hover:no-underline">
                <FaGithub aria-hidden="true" />
                <span>Star on GitHub</span>
              </a>
            </div>
            <RepoStats />
          </div>
        </div>
      </div>
    </header>
  );
}
