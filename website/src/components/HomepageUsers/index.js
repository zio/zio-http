import React from 'react';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './styles.module.css';

const usersList = [
  {
    name: 'Ziverge',
    image: 'img/users/ziverge.svg',
    url: 'https://ziverge.com'
  },
];

function User({ name, image, url }) {
  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      className={styles.user}>
      <img src={useBaseUrl(image)} alt={name} className={styles.userImage} />
    </a>
  );
}

export default function HomepageUsers() {
  return (
    <section className={styles.users}>
      <div className={`hp-inner ${styles.inner}`}>
        <div className={styles.head}>
          <h2 className={styles.title}>Who is Using ZIO HTTP?</h2>
          <p className="hp-lead">
            Organizations and projects building with ZIO HTTP in production
          </p>
        </div>
        <div className={styles.grid}>
          {usersList.map((user) => (
            <User key={user.name} {...user} />
          ))}
        </div>
        <p className={styles.join}>
          Are you using ZIO HTTP?{' '}
          <a
            className="hp-link"
            href="https://github.com/zio/zio-http"
            target="_blank"
            rel="noopener noreferrer">
            Let us know and join the list!
          </a>
        </p>
      </div>
    </section>
  );
}
