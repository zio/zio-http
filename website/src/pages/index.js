import React, { useEffect } from 'react';
import Layout from '@theme/Layout';
import HomepageHero from '@site/src/components/HomepageHero';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import HomepageCodeSnippet from '@site/src/components/HomepageCodeSnippet';
import HomepageZionomicon from '@site/src/components/HomepageZionomicon';
import HomepageEcosystem from '@site/src/components/HomepageEcosystem';
import HomepageUsers from '@site/src/components/HomepageUsers';
import Reveal from '@site/src/components/Reveal';
import useFullpageScroll from '@site/src/components/useFullpageScroll';

export default function Home() {
  // Homepage-only: mark <html> for scoped scroll offset, and enable the
  // fullPage-style one-section-per-gesture scrolling over the .fp-section
  // boundaries (hero + each main section).
  useEffect(() => {
    const root = document.documentElement;
    root.classList.add('homepage-snap');
    return () => root.classList.remove('homepage-snap');
  }, []);

  useFullpageScroll('.fp-section');

  return (
    <Layout
      title="ZIO HTTP"
      description="ZIO HTTP is a next-generation Scala framework for building scalable, correct, and efficient HTTP clients and servers.">
      <div className="ambient-bg" aria-hidden="true" />
      <div className="fp-section">
        <HomepageHero />
      </div>
      <main>
        <Reveal className="fp-section">
          <HomepageCodeSnippet />
        </Reveal>
        <Reveal className="fp-section">
          <HomepageFeatures />
        </Reveal>
        <Reveal className="fp-section">
          <HomepageEcosystem />
        </Reveal>
        <Reveal className="fp-section">
          <HomepageZionomicon />
        </Reveal>
        <Reveal className="fp-section">
          <HomepageUsers />
        </Reveal>
      </main>
    </Layout>
  );
}
