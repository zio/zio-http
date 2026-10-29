import React from 'react';
import clsx from 'clsx';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Link from '@docusaurus/Link';
import SectionWrapper from '@site/src/components/SectionWrapper';
import styles from './styles.module.css';

const EcosystemProjects = [
  {
    title: 'ZIO',
    description: 'A type-safe, composable library for async and concurrent programming in Scala',
    features: [
      'Build scalable applications with minimal overhead.',
      'Catch bugs at compile time with powerful type safety.',
      'Write concurrent code without deadlocks or race conditions.',
      'Seamless code for both sync and async operations.',
      'Never leak resources, even during failures.',
      'Build resilient systems that handle errors gracefully.',
      'Compose complex solutions from simple building blocks.',
      'Built-in dependency injection.',
      'Structured concurrency with simplicity and safety.',
      'Fine-grained interruption for cancelable operations.',
      'Integrated metrics and observability.',
      'Seamless interop with Java and Scala libraries.',
    ],
    link: 'https://zio.dev'
  },
  {
    title: 'ZIO Schema',
    description: 'A library for describing data types and their encodings in a composable way',
    features: [
      'Automatic derivation of schemas',
      'Support for many formats (JSON, Protobuf, etc.)',
      'Schema transformations and migrations',
      'Integration with ZIO HTTP'
    ],
    link: 'https://zio.dev/zio-schema/'
  },
  {
    title: 'ZIO Config',
    description: 'A type-safe, composable library for configuration management in Scala',
    features: [
      'Type-safe configuration descriptions',
      'Multiple source support (files, env vars, etc.)',
      'Documentation generation',
      'Validation and error reporting'
    ],
    link: 'https://zio.dev/zio-config/'
  },
  {
    title: 'ZIO Logging',
    description: 'A composable logging library for ZIO applications',
    features: [
      'Structured logging for ZIO applications',
      'Multiple backend support',
      'Context-aware logging',
      'Integration with MDC and log correlation'
    ],
    link: 'https://zio.dev/zio-logging/'
  },
  {
    title: 'ZIO Stream',
    description: 'A powerful streaming library for processing infinite data with finite resources',
    features: [
      'Fully asynchronous and non-blocking streaming',
      'Resource-safe processing of large data',
      'Rich set of combinators for data transformation',
      'Backpressure handling and flow control'
    ],
    link: 'https://zio.dev/reference/stream/'
  },
  {
    title: 'ZIO Test',
    description: 'A testing framework built on ZIO for writing comprehensive, concurrent tests',
    features: [
      'Property-based testing',
      'First-class support for asynchronous testing',
      'Compositional test aspects for reusable configurations',
      'Run test effects in parallel for faster test suites',
      'Integration with JUnit and other test frameworks',
    ],
    link: 'https://zio.dev/reference/test/'
  },
  {
    title: 'ZIO STM',
    description: 'Software Transactional Memory for composable concurrent state management',
    features: [
      'Atomic, isolated transactions',
      'Composable concurrent operations',
      'No deadlocks or race conditions',
      'Automatic retry of interrupted transactions'
    ],
    link: 'https://zio.dev/reference/stm/'
  }
];

export default function HomepageEcosystem() {
  const zioLogo = useBaseUrl('img/ZIO.png');
  // ZIO gets its own wide card; the rest are numbered library cards.
  const [zioProject, ...otherProjects] = EcosystemProjects;

  return (
    <SectionWrapper
      band
      eyebrow="Ecosystem"
      title="Ecosystem"
      subtitle="A complete toolkit for building scalable, resilient applications"
    >
      <div className={styles.zio}>
        <div className={styles.zioIntro}>
          <img src={zioLogo} alt="ZIO logo" className={styles.zioLogo} />
          <h3 className={styles.zioTitle}>{zioProject.title}</h3>
          <p className={styles.zioDescription}>{zioProject.description}</p>
          <Link className="hp-link" to={zioProject.link}>
            Learn More &rarr;
          </Link>
        </div>
        <ul className={clsx('hp-arrows', styles.zioFeatures)}>
          {zioProject.features.map((feature) => (
            <li key={feature}>{feature}</li>
          ))}
        </ul>
      </div>

      <div className={styles.cards}>
        {otherProjects.map((project, idx) => (
          <div key={project.title} className={styles.card}>
            <span className={styles.number}>{String(idx + 1).padStart(2, '0')}</span>
            <h3 className={styles.cardTitle}>{project.title}</h3>
            <p className={styles.cardDescription}>{project.description}</p>
            <ul className={clsx('hp-arrows', styles.cardFeatures)}>
              {project.features.map((feature) => (
                <li key={feature}>{feature}</li>
              ))}
            </ul>
            <Link className={clsx('hp-link', styles.cardLink)} to={project.link}>
              Learn More &rarr;
            </Link>
          </div>
        ))}
      </div>
    </SectionWrapper>
  );
}
