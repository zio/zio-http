import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './styles.module.css';

const bookUrl = 'https://www.zionomicon.com';

const lessons = [
  'Building scalable HTTP servers with minimal boilerplate',
  'How ZIO HTTP is modeled as "HTTP as a Function"',
  'Type-safe request handling',
  'Working with middleware and interceptors',
  'How to design your API declaratively',
];

export default function HomepageZionomicon() {
  const cover = useBaseUrl('img/zionomicon.png');

  return (
    <section className={clsx('hp-inner', styles.zionomicon)}>
      <div className={styles.copy}>
        <span className="hp-eyebrow">Zionomicon</span>
        <h2 className="hp-h2">Learn ZIO HTTP with Zionomicon</h2>
        <p className={styles.subtitle}>
          The comprehensive guide to building scalable applications with ZIO
        </p>
        <p className={styles.description}>
          Zionomicon is the definitive guide to ZIO, written by the creators of
          the library. It covers all aspects of the ZIO ecosystem, including a
          dedicated chapter on ZIO HTTP that teaches you how to build
          high-performance, type-safe web applications and APIs.
        </p>
        <div className={styles.lessons}>
          <span className={styles.lessonsTitle}>
            In the ZIO HTTP chapter, you'll learn:
          </span>
          <ul className={clsx('hp-arrows', styles.lessonList)}>
            {lessons.map((lesson) => (
              <li key={lesson}>{lesson}</li>
            ))}
          </ul>
        </div>
        <div className={styles.cta}>
          <Link className="hp-button hp-button--primary" to={bookUrl}>
            Get the Book for Free
          </Link>
        </div>
      </div>
      <Link className={styles.cover} to={bookUrl}>
        <img src={cover} alt="Zionomicon Book Cover" className={styles.coverImage} />
      </Link>
    </section>
  );
}
