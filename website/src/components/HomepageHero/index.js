import React from 'react';
import Link from '@docusaurus/Link';
import { FaRocket, FaShieldHalved, FaCubes, FaArrowRight } from 'react-icons/fa6';
import styles from './styles.module.css';

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
          <div className={styles.buttons}>
            <Link
              className="button button--secondary button--lg"
              to="/installation">
              <span>Get Started</span>
              <span> </span>
              <FaArrowRight className={styles.arrowIcon} />
            </Link>
          </div>
        </div>
      </div>
    </header>
  );
}
