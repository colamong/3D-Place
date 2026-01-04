import { useEffect, useState } from 'react';

export function usePainter({
  PAINT_MAX_CHARGES, PAINT_REGEN_INTERVAL_MS,
}: {
  PAINT_MAX_CHARGES: number;
  PAINT_REGEN_INTERVAL_MS: number;
}) {
  const [paintCharges, setPaintCharges] = useState(PAINT_MAX_CHARGES);
  const [paintRegenLeftMs, setPaintRegenLeftMs] = useState(0);

  useEffect(() => {
    if (paintCharges >= PAINT_MAX_CHARGES) { setPaintRegenLeftMs(0); return; }
    const started = Date.now();
    const nextAt = started + PAINT_REGEN_INTERVAL_MS;
    const id = window.setInterval(() => {
      const left = nextAt - Date.now();
      setPaintRegenLeftMs(Math.max(0, left));
      if (left <= 0) {
        window.clearInterval(id);
        setPaintCharges(c => Math.min(PAINT_MAX_CHARGES, c + 1));
      }
    }, 250);
    return () => window.clearInterval(id);
  }, [paintCharges, PAINT_MAX_CHARGES, PAINT_REGEN_INTERVAL_MS]);

  return { paintCharges, setPaintCharges, paintRegenLeftMs };
}

