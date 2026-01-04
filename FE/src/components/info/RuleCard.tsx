type RuleCardProps = {
  icon: React.ReactNode;
  children: React.ReactNode;
  intent?: 'info' | 'warn'; // 색상 바꾸고 싶을 때 사용
};

export default function RuleCard({
  icon,
  children,
  intent = 'info',
}: RuleCardProps) {
  const palette =
    intent === 'warn'
      ? 'bg-amber-50 border-amber-200'
      : 'bg-blue-50 border-blue-200';

  return (
    <div className={`flex items-start gap-3 rounded-2xl border ${palette} p-3`}>
      <span className="text-2xl leading-none shrink-0 select-none">{icon}</span>
      <p className="flex-1 text-sm md:text-base font-semibold leading-relaxed break-words whitespace-normal">
        {children}
      </p>
    </div>
  );
}
