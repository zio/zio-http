import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import styles from './styles.module.css';

export default function HomepageZionomicon() {
  return (
    <section className={styles.zionomicon}>
      <div className={styles.wideContainer}>
        <div className="row">
          <div className="col col--6">
            <div className={styles.ziconContent}>
              <h1>Learn ZIO HTTP with Zionomicon</h1>
              <p className={styles.ziconSubtitle}>
                The comprehensive guide to building scalable applications with ZIO
              </p>
              <p>
                Zionomicon is the definitive guide to ZIO, written by the creators of the library.
                It covers all aspects of the ZIO ecosystem, including a dedicated chapter on ZIO HTTP
                that teaches you how to build high-performance, type-safe web applications and APIs.
              </p>
              <p>
                In the ZIO HTTP chapter, you'll learn:
              </p>
              <ul>
                <li>Building scalable HTTP servers with minimal boilerplate</li>
                <li>How ZIO HTTP is modeled as "HTTP as a Function"</li>
                <li>Type-safe request handling</li>
                <li>Working with middleware and interceptors</li>
                <li>How to design your API declaratively</li>
              </ul>
              <div className={styles.buttonContainer}>
                <Link
                  className="button button--primary button--lg"
                  to="https://www.zionomicon.com"
                  target="_blank"
                  rel="noopener noreferrer">
                  Get the Book
                </Link>
              </div>
            </div>
          </div>
          <div className="col col--6">
            <div className={styles.ziconImageContainer}>
              <Link
                to="https://www.zionomicon.com"
                target="_blank">
                <img
                src="img/zionomicon.png"
                alt="Zionomicon Book Cover"
                className={styles.ziconImage}
              />
              </Link>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}