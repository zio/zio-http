import React from 'react';
import clsx from 'clsx';

// Shared section shell: centered eyebrow/title/gradient-rule/subtitle header,
// then the section's own content as children.
export default function SectionWrapper({
  eyebrow,
  title,
  subtitle,
  className,
  children,
}) {
  return (
    <section className={clsx('section-modern', className)}>
      {title ? (
        <div className="container section-modern__head">
          {eyebrow ? <span className="eyebrow">{eyebrow}</span> : null}
          <h2 className="section-title">{title}</h2>
          <div className="gradient-rule" />
          {subtitle ? <p className="section-subtitle">{subtitle}</p> : null}
        </div>
      ) : null}
      {children}
    </section>
  );
}
