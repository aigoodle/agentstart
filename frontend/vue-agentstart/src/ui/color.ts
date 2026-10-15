/** Choose the more legible foreground for a solid hex accent (WCAG luminance). */
export function getAgentStartColorForeground(color: string): string {
  const hex = color.replace('#', '');
  if (!/^(?:[\da-f]{3}|[\da-f]{6})$/i.test(hex)) return '#ffffff';
  const full = hex.length === 3 ? [...hex].map(value => value + value).join('') : hex;
  const channels = [0, 2, 4].map(index => {
    const value = parseInt(full.slice(index, index + 2), 16) / 255;
    return value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4;
  });
  const luminance = channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
  return (luminance + .05) / .05 > 1.05 / (luminance + .05) ? '#09090b' : '#ffffff';
}

/** Keep the chosen hue while making a primary surface readable with white text. */
export function getAgentStartAccessiblePrimary(color: string): string {
  const value = color.replace('#', '');
  if (!/^(?:[\da-f]{3}|[\da-f]{6})$/i.test(value)) return color;
  const hex = value.length === 3 ? [...value].map(c => c + c).join('') : value;
  let channels = [0, 2, 4].map(i => parseInt(hex.slice(i, i + 2), 16));
  const luminance = () => channels.map(c => {
    const s = c / 255;
    return s <= .04045 ? s / 12.92 : ((s + .055) / 1.055) ** 2.4;
  }).reduce((sum, c, i) => sum + c * [.2126, .7152, .0722][i]!, 0);
  while (1.05 / (luminance() + .05) < 4.7) channels = channels.map(c => Math.floor(c * .97));
  return '#' + channels.map(c => c.toString(16).padStart(2, '0')).join('');
}
