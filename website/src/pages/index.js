import React from 'react';
import Layout from '@theme/Layout';
import HomepageHero from '@site/src/components/HomepageHero';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import HomepageCodeSnippet from '@site/src/components/HomepageCodeSnippet';
import HomepageZionomicon from '@site/src/components/HomepageZionomicon';
import HomepageEcosystem from '@site/src/components/HomepageEcosystem';
import HomepageUsers from '@site/src/components/HomepageUsers';

export default function Home() {
  return (
    <Layout
      title="ZIO HTTP"
      description="ZIO HTTP is a next-generation Scala framework for building scalable, correct, and efficient HTTP clients and servers.">
      <HomepageHero />
      <main>
        <HomepageCodeSnippet />
        <HomepageFeatures />
        <HomepageEcosystem />
        <HomepageZionomicon />
        <HomepageUsers />
      </main>
    </Layout>
  );
}