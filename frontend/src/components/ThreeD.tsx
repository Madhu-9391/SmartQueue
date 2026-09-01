import React, { ReactNode } from 'react';

export const Scene = ({ children, className = '' }: { children: ReactNode; className?: string }) => (
  <section className={`scene-3d ${className}`}>
    <div className="scene-grid" aria-hidden="true" />
    <div className="scene-orb scene-orb-a" aria-hidden="true" />
    <div className="scene-orb scene-orb-b" aria-hidden="true" />
    {children}
  </section>
);

export const GlassPanel = ({ children, className = '' }: { children: ReactNode; className?: string }) => (
  <div className={`glass-panel ${className}`}>{children}</div>
);

export const HoloMetric = ({ label, value, detail, tone = 'teal' }: {
  label: string; value: string | number; detail?: string; tone?: 'teal' | 'blue' | 'purple' | 'amber';
}) => (
  <div className={`holo-metric tone-${tone}`}>
    <div className="holo-metric-glow" />
    <p>{label}</p>
    <strong>{value}</strong>
    {detail && <span>{detail}</span>}
  </div>
);
