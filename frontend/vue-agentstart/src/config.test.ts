import { afterEach, describe, expect, it } from 'vitest';

import { setAgentStartAppearance } from './config';

describe('setAgentStartAppearance', () => {
  afterEach(() => {
    const root = document.documentElement;
    root.classList.remove('dark');
    delete root.dataset.theme;
    delete root.dataset.visual;
    root.removeAttribute('style');
  });

  it('applies the public theme and visual contracts', () => {
    setAgentStartAppearance({ theme: 'dark', visual: 'tech' });

    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(document.documentElement.dataset.visual).toBe('tech');
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    setAgentStartAppearance({ theme: 'light', visual: 'normal' });
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(document.documentElement.dataset.visual).toBe('normal');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });

  it('maps one primary color to base, knowledge and HUD tokens', () => {
    setAgentStartAppearance({ primaryColor: '#722ed1' });

    const style = document.documentElement.style;
    expect(style.getPropertyValue('--color-as-primary')).toBe('#722ed1');
    expect(style.getPropertyValue('--as-theme-primary')).toBe('#722ed1');
    expect(style.getPropertyValue('--kh-color-primary')).toBe('#722ed1');
  });

  it('supports a custom appearance scope', () => {
    const target = document.createElement('section');
    setAgentStartAppearance(
      { theme: 'dark', visual: 'normal', primaryColor: 'rebeccapurple' },
      target,
    );

    expect(target.dataset.theme).toBe('dark');
    expect(target.dataset.visual).toBe('normal');
    expect(target.style.getPropertyValue('--as-theme-primary')).toBe('rebeccapurple');
  });
});
