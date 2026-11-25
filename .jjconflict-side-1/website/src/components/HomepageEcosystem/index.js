import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
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
    link: 'https://zio.dev',
    icon: {
      type: 'image',
      value: 'img/ZIO.png'
    }
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
    link: 'https://zio.dev/zio-schema/',
    icon: {
      type: 'text',
      value: '🧬'
    }
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
    link: 'https://zio.dev/zio-config/',
    icon: {
      type: 'text',
      value: '🛠️'
    }
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
    link: 'https://zio.dev/zio-logging/',
    icon: {
      type: 'text',
      value: '📝'
    }
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
    link: 'https://zio.dev/reference/stream/',
    icon: {
      type: 'text',
      value: '🔗'
    }
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
    link: 'https://zio.dev/reference/test/',
    icon: {
      type: 'text',
      value: '✅ '
    }
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
    link: 'https://zio.dev/reference/stm/',
    icon: {
      type: 'text',
      value: '🔒️'
    }
  }
];

export default function HomepageEcosystem() {
  // Separate ZIO from other projects
  const zioProject = EcosystemProjects[0];
  const otherProjects = EcosystemProjects.slice(1);

  return (
    <section className={styles.ecosystem}>
      <div className={styles.wideContainer}>
        <div className={clsx('row', styles.ecosystemHeader)}>
          <div className="col col--12 text--center">
            <h2 class="sectionHeader">Ecosystem</h2>
            <p className={styles.ecosystemSubtitle}>
              A complete toolkit for building scalable, resilient applications
            </p>
          </div>
        </div>

        {/* ZIO in its own row */}
        <div className={clsx('row', styles.ecosystemCards)}>
          <div className={clsx('col col--8 col--offset-2', styles.mainProjectCol)}>
            <div className={styles.ecosystemCard}>
              <div className={styles.mainProjectHeader}>
                {zioProject.icon.type === 'image' ? (
                  <img
                    src={zioProject.icon.value}
                    alt="ZIO logo"
                    className={styles.mainProjectLogo}
                  />
                ) : (
                  <div className={styles.mainProjectLogoText}>
                    {zioProject.icon.value}
                  </div>
                )}
                <h3 className={styles.srOnly}>{zioProject.title}</h3>
              </div>
              <p className={styles.ecosystemCardDescription}>{zioProject.description}</p>
              <ul className={styles.ecosystemCardFeatures}>
                {zioProject.features.map((feature, fidx) => (
                  <li key={fidx}>{feature}</li>
                ))}
              </ul>
              <div className={styles.ecosystemCardFooter}>
                <Link
                  className={clsx('button button--outline button--primary', styles.ecosystemCardButton)}
                  to={zioProject.link}
                  target="_blank"
                  rel="noopener noreferrer">
                  Learn More
                </Link>
              </div>
            </div>
          </div>
        </div>

        {/* Other projects in a second row */}
        <div className={clsx('row', styles.ecosystemCards)}>
          {otherProjects.map((project, idx) => (
            <div key={idx} className={clsx('col col--4 col--md-6', styles.ecosystemCardCol)}>
              <div className={styles.ecosystemCard}>
                <div className={styles.ecosystemCardHeader}>
                  <div className={styles.ecosystemCardIcon}>
                    {project.icon.type === 'image' ? (
                      <img src={project.icon.value} alt={`${project.title} icon`} />
                    ) : (
                      project.icon.value
                    )}
                  </div>
                  <h3>{project.title}</h3>
                </div>
                <p className={styles.ecosystemCardDescription}>{project.description}</p>
                <ul className={styles.ecosystemCardFeatures}>
                  {project.features.map((feature, fidx) => (
                    <li key={fidx}>{feature}</li>
                  ))}
                </ul>
                <div className={styles.ecosystemCardFooter}>
                  <Link
                    className={clsx('button button--outline button--primary', styles.ecosystemCardButton)}
                    to={project.link}
                    target="_blank"
                    rel="noopener noreferrer">
                    Learn More
                  </Link>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}