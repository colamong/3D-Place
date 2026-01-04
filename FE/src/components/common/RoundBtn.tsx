import React, { forwardRef } from 'react';

type Props = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  className?: string;
};

const RoundBtn = forwardRef<HTMLButtonElement, Props>(
  ({ className = '', children, ...rest }, ref) => {
    const sizeClass = className ? '' : 'w-10 h-10'; // className이 없을 때만 기본 사이즈

    return (
      <button
        ref={ref}
        {...rest}
        className={[
          sizeClass,
          'rounded-full bg-white shadow-md hover:shadow-lg cursor-pointer',
          'border border-black/5 flex items-center justify-center pointer-events-auto',
          // pressed/disabled visual states
          'aria-pressed:bg-blue-500 aria-pressed:text-white',
          'disabled:opacity-50 disabled:cursor-not-allowed',
          className,
        ].join(' ')}
      >
        {children}
      </button>
    );
  },
);

RoundBtn.displayName = 'RoundBtn';
export default RoundBtn;
