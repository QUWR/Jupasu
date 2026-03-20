'use client';

import Image from 'next/image';
import { ChevronLeft } from 'lucide-react';
import SignUpForm from '@/features/auth/components/SignUpForm';
import Step2Taste from '@/features/onboarding/components/Step2Taste';
import Step3WineReview from '@/features/onboarding/components/Step3WineReview';
import { useSignup } from '@/features/auth/hooks/useSignup';

export default function SignupPage() {
  const {
    step,
    setNickname,
    nicknameMessage,
    isValidatingNickname,
    isNicknameAvailable,
    mutations,
    handleStep1Success,
    handleNext,
    handleBack,
    onboardingData,
    setOnboardingData,
    authCodeTimeLeft,
    isAuthCodeExpired,
    resendSeconds,
    isSentOnce,
  } = useSignup();

  return (
    <div className="flex min-h-screen flex-col px-6 py-10">
      {/* Header Area */}
      <header className="relative mx-auto mb-8 flex w-full max-w-md flex-col items-center">
        <button
          onClick={handleBack}
          className="text-text-main/60 hover:text-text-main absolute top-0 left-0 -ml-2 p-2 transition-colors"
        >
          <ChevronLeft size={28} />
        </button>

        <div className="flex flex-col items-center space-y-4 pt-2">
          <div className="relative h-16 w-16">
            <Image src="/logo.png" alt="Logo" fill className="object-contain" priority />
          </div>
          <div className="text-center">
            <h1 className="text-text-main text-2xl font-black tracking-tight">Join JUPASU</h1>
            <p className="text-text-main/40 mt-1 text-xs font-bold">
              와인의 세계에 오신 것을 환영합니다!
            </p>
          </div>
        </div>
      </header>

      {/* Stepper Indicator */}
      <div className="mx-auto mb-10 flex w-full max-w-md gap-2">
        {[1, 2, 3].map((s) => (
          <div
            key={s}
            className={`h-1.5 flex-1 rounded-full transition-all duration-500 ${
              s <= step ? 'bg-primary-700' : 'bg-primary-100'
            }`}
          />
        ))}
      </div>

      {/* Step Content */}
      <main className="mx-auto w-full max-w-md flex-1">
        {step === 1 && (
          <SignUpForm
            mutations={mutations}
            setNickname={setNickname}
            nicknameMessage={nicknameMessage}
            isValidatingNickname={isValidatingNickname}
            isNicknameAvailable={isNicknameAvailable}
            onSuccess={handleStep1Success}
            authCodeTimeLeft={authCodeTimeLeft}
            isAuthCodeExpired={isAuthCodeExpired}
            resendSeconds={resendSeconds}
            isSentOnce={isSentOnce}
          />
        )}
        {step === 2 && (
          <Step2Taste
            data={onboardingData}
            setData={setOnboardingData}
            onNext={handleNext}
            isPending={mutations.updatePreference.isPending}
          />
        )}
        {step === 3 && <Step3WineReview onNext={handleNext} onPrev={handleBack} />}
      </main>
    </div>
  );
}
