import React from 'react';
import clsx from 'clsx';
import { LuZap, LuBlocks, LuCloud, LuWorkflow } from 'react-icons/lu';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'High Performance & Non-blocking',
    Icon: LuZap,
    description: (
      <>
        Event-driven, non-blocking I/O on Netty — high throughput, low latency.
      </>
    ),
  },
  {
    title: 'Unified ZIO Experience',
    Icon: LuBlocks,
    description: (
      <>
        Fibers, typed errors, and resource safety, plus the full ZIO ecosystem.
      </>
    ),
  },
  {
    title: 'Cloud-Native Support',
    Icon: LuCloud,
    description: (
      <>
        Massive concurrency over thousands of fibers — scale out under load.
      </>
    ),
  },
  {
    title: 'Type-driven Endpoints',
    Icon: LuWorkflow,
    description: (
      <>
        Imperative or schema-driven endpoints, type-checked at compile time.
      </>
    ),
  },
];

function Feature({Icon, title, description, features = []}) {
  return (
    <div className={clsx('col col--4', styles.featureCardCol)}>
      <div className={styles.featureCard}>
        <div className={styles.featureCardHeader}>
          <div className={styles.featureCardIcon}>
            <Icon role="img" aria-hidden="true" />
          </div>
          <h3>{title}</h3>
        </div>
        <p className={styles.featureCardDescription}>{description}</p>
        {features.length > 0 && (
          <ul className={styles.featureCardFeatures}>
            {features.map((feature, idx) => (
              <li key={idx}>{feature}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className={styles.wideContainer}>
        <div className={clsx('row', styles.featuresHeader)}>
          <div className="col col--12 text--center">
            <h2 className="sectionHeader">Key Features</h2>
            <p className={styles.featuresSubtitle}>
              Build high-performance, scalable web applications with ZIO HTTP
            </p>
          </div>
        </div>

        <div className={clsx('row', styles.featureCards)}>
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}