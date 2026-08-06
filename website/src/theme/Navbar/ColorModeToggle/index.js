import React from 'react';
import ColorModeToggle from '@theme-original/Navbar/ColorModeToggle';
import OnboardAgentNavbarButton from '@site/src/components/OnboardAgentButton/NavbarButton';

// Render the compact Onboard Agent button just before the light/dark theme
// switch — i.e. after the GitHub link (last right item) and before the toggle.
export default function ColorModeToggleWrapper(props) {
  return (
    <>
      <OnboardAgentNavbarButton />
      <ColorModeToggle {...props} />
    </>
  );
}
