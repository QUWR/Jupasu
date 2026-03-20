import Image from 'next/image';
import SignInForm from '@/features/auth/components/SignInForm';

interface LoginPageProps {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const params = await searchParams;
  const error = typeof params.error === 'string' ? params.error : undefined;

  return (
    <div className="flex min-h-screen flex-col px-6 py-12">
      <div className="mt-12 mb-10 flex flex-col items-center justify-center space-y-6">
        <div className="relative h-32 w-32">
          <Image src="/logo.png" alt="JUPASU Logo" fill className="object-contain" priority />
        </div>
        <div className="space-y-2 text-center">
          <h1 className="text-text-main text-[28px] font-extrabold">Welcome Back</h1>
          <p className="text-text-main/60 text-base font-medium">다시 만나서 반가워요!</p>
        </div>
      </div>

      <main className="mx-auto w-full max-w-md">
        <SignInForm errorCode={error} />
      </main>
    </div>
  );
}
