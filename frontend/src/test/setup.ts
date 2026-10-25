
import '@testing-library/jest-dom';






if (typeof Element !== 'undefined' && !Element.prototype.animate) {
  Element.prototype.animate = () =>
    ({
      cancel: () => {},
      finish: () => {},
      onfinish: null,
    }) as unknown as Animation;
}
