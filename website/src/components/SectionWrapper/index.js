import React from 'react';
import clsx from 'clsx';
import styles from './styles.module.css';

// Shared section shell: left-aligned eyebrow, title and lead, then the
// section's own content as children.
export default function SectionWrapper({
  eyebrow,
  title,
  subtitle,
  band = false,
  className,
  children,
}) {
  return (
    <section className={clsx(band && 'hp-band', className)}>
      <div className={clsx('hp-inner', styles.inner)}>
        <div className={styles.head}>
          <span className="hp-eyebrow">{eyebrow}</span>
          <h2 className="hp-h2">{title}</h2>
          <p className="hp-lead">{subtitle}</p>
        </div>
        {children}
      </div>
    </section>
  );
}
