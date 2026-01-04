import { cn } from '@/lib/cn';
import * as React from 'react';

export function Separator({
  className,
  ...props
}: React.HTMLAttributes<HTMLHRElement>) {
  return (
    <hr
      className={cn('my-2 h-px w-full border-0 bg-gray-200', className)}
      {...props}
    />
  );
}
