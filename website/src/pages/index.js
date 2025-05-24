import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import HomepageShowcases from '@site/src/components/HomepageShowcases';
import HomepageZionomicon from '@site/src/components/HomepageZionomicon';
import HomepageEcosystem from '@site/src/components/HomepageEcosystem';
import HomepageUsers from '@site/src/components/HomepageUsers';
import styles from './index.module.css';
import { FaArrowRight } from 'react-icons/fa6';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <h1 className="hero__title">{siteConfig.title}</h1>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link
            className="button button--secondary button--lg"
            to="/docs/installation">
              <span>Get Started</span><span> </span><FaArrowRight className={styles.arrowIcon} />
          </Link>
        </div>
      </div>
    </header>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={`${siteConfig.title}`}
      description="ZIO HTTP is a next-generation Scala framework for building scalable, correct, and efficient HTTP clients and servers.">
      <HomepageHeader />
      <main>
        <HomepageShowcases />
        <HomepageFeatures />
        <HomepageEcosystem />
        <HomepageZionomicon />
        <HomepageUsers />
      </main>
    </Layout>
  );
}