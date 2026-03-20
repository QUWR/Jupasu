export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-background min-h-screen">
      <div className="app-shell page-gutter top-safe bottom-safe min-h-screen">{children}</div>
    </div>
  );
}
