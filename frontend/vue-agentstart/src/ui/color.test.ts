import { describe, expect, it } from 'vitest';
import { getAgentStartAccessiblePrimary, getAgentStartColorForeground } from './color';

describe('primary surface contrast', () => {
  it.each(['#00ccff', '#1677ff', '#fadb14', '#52c41a', '#fff', '#722ed1'])('keeps white text readable on %s', color => {
    const primary = getAgentStartAccessiblePrimary(color);
    const linear = [1, 3, 5].map(i => {
      const s = parseInt(primary.slice(i, i + 2), 16) / 255;
      return s <= .04045 ? s / 12.92 : ((s + .055) / 1.055) ** 2.4;
    });
    const luminance = linear[0] * .2126 + linear[1] * .7152 + linear[2] * .0722;
    expect(1.05 / (luminance + .05)).toBeGreaterThanOrEqual(4.7);
    expect(getAgentStartColorForeground(primary)).toBe('#ffffff');
  });
  it('preserves already readable colors and custom CSS values', () => {
    expect(getAgentStartAccessiblePrimary('#722ed1')).toBe('#722ed1');
    expect(getAgentStartAccessiblePrimary('var(--brand)')).toBe('var(--brand)');
  });
});
