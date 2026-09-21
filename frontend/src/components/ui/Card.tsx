interface CardProps extends React.HTMLAttributes<HTMLDivElement> {}

export function Card({ children, className = '', ...props }: CardProps) {
  return (
    <div className={`bg-white rounded-xl shadow-sm border border-gray-200 ${className}`} {...props}>
      {children}
    </div>
  );
}