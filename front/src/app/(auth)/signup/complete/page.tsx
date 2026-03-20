'use client';

import { useEffect, useCallback } from 'react';
import Image from 'next/image';
import { X } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { authToken } from '@/features/auth/utils/auth-token';
import { useAuthStore } from '@/stores/auth.store';

export default function SignupCompletePage() {
  const router = useRouter();

  const handleRedirect = useCallback(() => {
    authToken.remove();
    useAuthStore.getState().clearAuth();
    router.push('/login');
  }, [router]);

  useEffect(() => {
    const timer = setTimeout(handleRedirect, 2000);
    return () => clearTimeout(timer);
  }, [handleRedirect, router]);

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6">
      <div className="animate-in fade-in zoom-in flex flex-col items-center space-y-8 duration-700">
        <div className="relative h-40 w-40">
          <Image src="/logo.png" alt="Logo" fill className="object-contain" priority />
        </div>
        <div className="space-y-3 text-center">
          <h1 className="text-text-main text-[32px] font-extrabold tracking-tight">환영합니다!</h1>
          <p className="text-text-main/60 text-base font-medium">
            와인의 세계에 오신 것을 환영합니다!
          </p>
        </div>
      </div>

      {/* Bottom Toast-style Banner */}
      <div className="animate-in slide-in-from-bottom-8 absolute right-6 bottom-6 left-6 delay-300 duration-1000">
        <div className="border-primary-100 flex items-center justify-between rounded-[32px] border bg-white p-3 shadow-xl">
          <div className="flex items-center gap-5">
            <span className="text-2xl">🍞</span>
            <div className="flex flex-col">
              <span className="text-text-main text-sm font-black">
                잠시 후 로그인 페이지로 이동합니다.
              </span>
              <button
                onClick={handleRedirect}
                className="text-left text-xs font-bold text-[#B36262] hover:underline"
              >
                직접 이동하기
              </button>
            </div>
          </div>
          <button onClick={handleRedirect} className="text-text-main/30">
            <X size={20} />
          </button>
        </div>
      </div>
    </div>
  );
}
