import React from 'react';
import SectionWrapper from '@site/src/components/SectionWrapper';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Type-Driven Endpoints',
    description: (
      <>
        Declarative or imperative endpoints, type-checked against the
        description at compile time.
      </>
    ),
  },
  {
    title: 'OpenAPI, Both Ways',
    description: (
      <>
        Generate OpenAPI docs from your endpoints — or endpoints from an
        OpenAPI spec.
      </>
    ),
  },
  {
    title: 'Schema-Powered Codecs',
    description: (
      <>
        Encode and decode bodies via ZIO Schema: JSON, Protobuf, Avro, and
        Thrift.
      </>
    ),
  },
  {
    title: 'Composable Middleware',
    description: (
      <>
        Auth, CORS, logging, and metrics as reusable, composable middleware.
      </>
    ),
  },
  {
    title: 'Real-Time Ready',
    description: (
      <>
        Built-in WebSockets and Server-Sent Events for live, streaming apps.
      </>
    ),
  },
  {
    title: 'First-Class Testing',
    description: (
      <>
        Test routes and clients without a live server, using the testkit.
      </>
    ),
  },
];

export default function HomepageFeatures() {
  return (
    <SectionWrapper
      eyebrow="Key features"
      title="Key Features"
      subtitle="Build high-performance, scalable web applications with ZIO HTTP"
    >
      <ul className={styles.grid}>
        {FeatureList.map(({ title, description }, idx) => (
          <li key={title} className={styles.feature}>
            <span className={styles.number}>{String(idx + 1).padStart(2, '0')}</span>
            <h3 className={styles.title}>{title}</h3>
            <p className={styles.description}>{description}</p>
          </li>
        ))}
      </ul>
    </SectionWrapper>
  );
}
