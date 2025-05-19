import React from 'react';
import styles from './styles.module.css';

const usersList = [
  {
    name: 'Brilliant solutions for innovative companies',
    image: 'img/users/ziverge.svg',
    url: 'https://ziverge.com'
  },
];

export default function HomepageUsers() {
  // Calculate the width style based on the number of users
  const getUserItemStyle = () => {
    const count = usersList.length;
    if (count === 1) {
      return { width: '300px' }; // Single user gets fixed width
    } else if (count === 2) {
      return { width: '300px' }; // Two users get fixed width
    } else if (count <= 4) {
      return { width: `calc(${100/count}% - 2rem)` }; // Equal distribution for 3-4 users
    }
    // Default styling (flex with calc) is applied via CSS for 5+ users
    return {};
  };

  return (
    <section className={styles.users}>
      <div className={styles.wideContainer}>
        <div className="row">
          <div className="col col--12 text--center">
            <h2>Who's Using ZIO HTTP</h2>
            <p className={styles.subtitle}>
              Organizations and projects building with ZIO HTTP in production
            </p>
          </div>
        </div>
        <div className={styles.usersGrid}>
          {usersList.map((user, idx) => (
            <a
              key={idx}
              href={user.url}
              target="_blank"
              rel="noopener noreferrer"
              className={styles.userItem}
              style={getUserItemStyle()}
            >
              <div className={styles.userImageContainer}>
                <img
                  src={user.image}
                  alt={`${user.name} logo`}
                  className={styles.userImage}
                  onError={(e) => {
                    // Fallback for missing images
                    e.target.src = "/api/placeholder/200/100";
                    // Create text element with company name as fallback
                    const parent = e.target.parentNode;
                    const text = document.createElement('div');
                    text.className = styles.userNameFallback;
                    text.textContent = user.name;
                    parent.appendChild(text);
                  }}
                />
              </div>
              <div className={styles.userName}>{user.name}</div>
            </a>
          ))}
        </div>
        <div className="row">
          <div className="col col--12 text--center">
            <p className={styles.joinCommunity}>
              Are you using ZIO HTTP?{' '}
              <a
                href="https://github.com/zio/zio-http"
                target="_blank"
              >
                Let us know and join the list!
              </a>
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}