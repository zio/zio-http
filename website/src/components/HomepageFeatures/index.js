import React from 'react';
import clsx from 'clsx';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'High Performance & Non-blocking',
    Svg: require('@site/static/img/undraw_docusaurus_mountain.svg').default,
    description: (
      <>
        ZIO HTTP is powered by Netty and ZIO’s asynchronous runtime, so all I/O is
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
        <p>
          Built entirely on ZIO's effect system, ZIO HTTP gives you built-in
          support for lightweight fibers (highly concurrent "threads"),
          structured error handling, resource safety, and composability.
          You inherit all the benefits of ZIO in your web applications.
        </p>
        <p>
          You have also the rich ecosystem of ZIO libraries at your disposal,
          including ZIO Schema, ZIO Config, and ZIO logging.
        </p>
      </>
    ),
  },
  {
    title: 'Cloud-Native Support',
    Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
    description: (
      <>
        <p>
            Designed for cloud-scale deployments, ZIO HTTP supports massive
            concurrency and parallelism inherently. It efficiently manages
            thousands of fibers (lightweight threads) and connections, so your
             services can scale horizontally under load.
        </p>

        <p>
            Because the native support of ZIO, features like structured error
            handling, built-in retries, automatic resource cleanup mean faults
            are contained. Also, you have access to observability features like
            structured logging, metrics, tracing, and monitoring.
        </p>
      </>
    ),
  },
  {
    title: 'Type-driven Endpoints',
    Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
    description: (
      <>
        <p>
            ZIO HTTP supports both imperative routing and declarative,
            schema-driven endpoints. You describe request and response schemas
            in types and the framework type-checks your handler logic against
            them at compile time. This catches many errors before you even run
            the code and eliminates a lot of boilerplate.
        </p>

        <p>
            This is a key feature
            to generate OpenAPI documentation automatically or generate client
            code from OpenAPI specs which leads to seamless API visibility.
        </p>
      </>
    ),
  },
];

function Feature({Svg, title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.slice(0, 3).map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
        <div className="row">
          <div className="col col--4"></div>
          <Feature key={3} {...FeatureList[3]} />
          <div className="col col--4"></div>
        </div>
      </div>
    </section>
  );
}
