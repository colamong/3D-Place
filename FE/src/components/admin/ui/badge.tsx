import { cn } from '@/lib/cn';
import * as React from 'react';

type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline';

export function Badge({
  className,
  variant = 'default',
  ...props
}: React.HTMLAttributes<HTMLSpanElement> & { variant?: BadgeVariant }) {
  const styles: Record<BadgeVariant, string> = {
    default: 'bg-black text-white',
    secondary: 'bg-gray-100 text-gray-900',
    destructive: 'bg-red-600 text-white',
    outline: 'border border-current text-foreground',
  };
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
        styles[variant],
        className,
      )}
      {...props}
    />
  );
}
