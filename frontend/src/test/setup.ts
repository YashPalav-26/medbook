// Test setup for Vitest
import '@testing-library/jest-dom';

/**
 * jsdom does not implement Element.animate() (the Web Animations API), which
 * Sonner's toast transitions call on mount. A tiny no-op stub keeps the third-
 * party toast library working in tests without changing production behavior.
 */
if (typeof Element !== 'undefined' && !Element.prototype.animate) {
  Element.prototype.animate = () =>
    ({
      cancel: () => {},
      finish: () => {},
      onfinish: null,
    }) as unknown as Animation;
}
