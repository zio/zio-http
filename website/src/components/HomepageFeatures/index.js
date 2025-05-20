import React from 'react';
import clsx from 'clsx';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'High Performance & Non-blocking',
    Svg: require('@site/static/img/undraw_docusaurus_mountain.svg').default,
    description: (
      <>
        ZIO HTTP is powered by Netty and ZIO's asynchronous runtime, so all I/O is
        event-driven and non-blocking. This yields extremely high throughput and
        low latency with minimal resource use.
      </>
    ),
  },
  {
    title: 'Native ZIO Integration and Ecosystem',
    Svg: require('@site/static/img/undraw_docusaurus_tree.svg').default,
    description: (
      <>
        Built entirely on ZIO's effect system, ZIO HTTP gives you built-in
        support for lightweight fibers (highly concurrent "threads"),
        structured error handling, resource safety, and composability.
        You inherit all the benefits of ZIO in your web applications.
      </>
    ),
  },
  {
    title: 'Cloud-Native Support',
    Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
    description: (
      <>
        Designed for cloud-scale deployments, ZIO HTTP supports massive
        concurrency and parallelism inherently. It efficiently manages
        thousands of fibers (lightweight threads) and connections, so your
        services can scale horizontally under load.
      </>
    ),
  },
  {
    title: 'Type-driven Endpoints',
    Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
    description: (
      <>
        ZIO HTTP supports both imperative routing and declarative,
        schema-driven endpoints. You describe request and response schemas
        in types and the framework type-checks your handler logic against
        them at compile time.
      </>
    ),
  },
];

function Feature({Svg, title, description, features = []}) {
  return (
    <div className={clsx('col col--4', styles.featureCardCol)}>
      <div className={styles.featureCard}>
        <div className={styles.featureCardHeader}>
          <div className={styles.featureCardIcon}>
            <Svg role="img" />
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