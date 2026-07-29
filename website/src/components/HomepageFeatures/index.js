import React from 'react';
import clsx from 'clsx';
import {
  LuShieldCheck,
  LuFileText,
  LuBraces,
  LuLayers,
  LuRadio,
  LuFlaskConical,
} from 'react-icons/lu';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Type-Driven Endpoints',
    Icon: LuShieldCheck,
    description: (
      <>
        Declarative or imperative endpoints, type-checked against the
        description at compile time.
      </>
    ),
  },
  {
    title: 'OpenAPI, Both Ways',
    Icon: LuFileText,
    description: (
      <>
        Generate OpenAPI docs from your endpoints — or endpoints from an
        OpenAPI spec.
      </>
    ),
  },
  {
    title: 'Schema-Powered Codecs',
    Icon: LuBraces,
    description: (
      <>
        Encode and decode bodies via ZIO Schema: JSON, Protobuf, Avro, and
        Thrift.
      </>
    ),
  },
  {
    title: 'Composable Middleware',
    Icon: LuLayers,
    description: (
      <>
        Auth, CORS, logging, and metrics as reusable HandlerAspects.
      </>
    ),
  },
  {
    title: 'Real-Time Ready',
    Icon: LuRadio,
    description: (
      <>
        Built-in WebSockets and Server-Sent Events for live, streaming apps.
      </>
    ),
  },
  {
    title: 'First-Class Testing',
    Icon: LuFlaskConical,
    description: (
      <>
        Test routes and clients without a live server, using the testkit.
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