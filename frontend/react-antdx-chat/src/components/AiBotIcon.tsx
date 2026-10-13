import React, { useId } from 'react';

// AI 助手图标（内联 SVG）
// 用 useId() 让每个实例的 <defs> id 唯一, 避免多实例渲染时渐变互相覆盖.
export const AiBotIcon: React.FC<{
  size?: number;
  className?: string;
  style?: React.CSSProperties;
}> = ({ size = 24, className, style }) => {
  const uid = useId().replace(/[:]/g, '');
  const gradId = `aiBotGrad-${uid}`;
  const sparkleId = `aiBotSparkle-${uid}`;
  return (
    <svg
      viewBox="0 0 48 48"
      width={size}
      height={size}
      fill="none"
      className={className}
      style={style}
      aria-hidden
    >
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="48" y2="48" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#60a5fa" />
          <stop offset="55%" stopColor="#a78bfa" />
          <stop offset="100%" stopColor="#f472b6" />
        </linearGradient>
        <linearGradient id={sparkleId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#fde68a" />
          <stop offset="100%" stopColor="#fbbf24" />
        </linearGradient>
      </defs>
      {/* antenna */}
      <line
        x1="24"
        y1="4"
        x2="24"
        y2="11"
        stroke={`url(#${gradId})`}
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <circle cx="24" cy="4" r="2.5" fill={`url(#${gradId})`} />
      {/* ears */}
      <rect x="3" y="22" width="6" height="10" rx="3" fill={`url(#${gradId})`} opacity="0.85" />
      <rect x="39" y="22" width="6" height="10" rx="3" fill={`url(#${gradId})`} opacity="0.85" />
      {/* head */}
      <rect x="8" y="11" width="32" height="30" rx="11" fill={`url(#${gradId})`} />
      {/* inner screen */}
      <rect x="12" y="15" width="24" height="22" rx="8" fill="rgba(255,255,255,0.16)" />
      {/* eyes */}
      <circle cx="18" cy="25" r="2.8" fill="#fff" />
      <circle cx="30" cy="25" r="2.8" fill="#fff" />
      <circle cx="18.6" cy="25.4" r="1.2" fill="#1e293b" />
      <circle cx="30.6" cy="25.4" r="1.2" fill="#1e293b" />
      {/* smile */}
      <path
        d="M18 32 Q24 36 30 32"
        stroke="#fff"
        strokeWidth="2"
        strokeLinecap="round"
        fill="none"
      />
      {/* sparkle */}
      <path
        d="M41 5 L42.2 8.5 L45.5 9.7 L42.2 10.9 L41 14.5 L39.8 10.9 L36.5 9.7 L39.8 8.5 Z"
        fill={`url(#${sparkleId})`}
      />
    </svg>
  );
};

export default AiBotIcon;
