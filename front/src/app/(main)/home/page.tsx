'use client';

import { useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import Chip from '@/components/ui/chip/Chip';
import { useWineQuickRecommendQuery } from '@/features/wine/hooks/useWineListQuery';
import { DrinkingSituation } from '@/features/wine/types/wine.types';

const SITUATION_MAP: Record<string, DrinkingSituation> = {
  혼술: 'ALONE',
  기념일: 'DATE',
  파티: 'PARTY',
};

const SITUATIONS = ['혼술', '기념일', '파티'];

export default function HomePage() {
  const [activeSituation, setActiveSituation] = useState('혼술');
  const { data: recommendations, isLoading } = useWineQuickRecommendQuery();

  // 'VIBE GALLERY'용 일반 추천 리스트
  const generalWines = recommendations?.general || [];

  // 상황별 추천 리스트 (현재 선택된 상황에 맞는 데이터 추출)
  const currentSituationEnum = SITUATION_MAP[activeSituation];
  const situationResult = recommendations?.bySituation.find(
    (s) => s.situation === currentSituationEnum,
  );
  const situationWines = situationResult?.recommendations || [];

  if (isLoading) {
    return (
      <div className="bg-background flex min-h-screen items-center justify-center">
        <div className="h-12 w-12 animate-spin rounded-full border-b-2 border-[#B36262]"></div>
      </div>
    );
  }

  return (
    <div className="bg-background min-h-screen pb-28">
      <div className="bg-background/88 sticky top-0 z-30 px-6 pt-4 pb-3 backdrop-blur-xl">
        <div className="flex justify-center">
          <div className="relative h-14 w-14">
            <Image src="/logo.png" alt="JUPASU" fill className="object-contain" />
          </div>
        </div>
      </div>

      <section className="px-6 pt-4 pb-6">
        <h2 className="text-text-main mb-4 text-xl font-black tracking-tight uppercase italic">
          VIBE GALLERY
        </h2>

        <div className="no-scrollbar flex gap-4 overflow-x-auto px-1 pb-4">
          {generalWines.length > 0 ? (
            generalWines.map((wine) => (
              <Link key={wine.wineId} href={`/wines/${wine.wineId}`} className="shrink-0">
                <article className="border-primary-100 relative w-[232px] overflow-hidden rounded-[22px] border bg-white shadow-[0_12px_26px_rgba(51,34,17,0.06)] transition-all active:scale-[0.985]">
                  <div className="absolute top-4 left-4 z-10 rounded-full bg-[#B36262] px-3.5 py-1.5 text-[11px] font-black text-white shadow-sm">
                    {wine.matchRate}% MATCH
                  </div>

                  <div className="flex aspect-[0.92] items-center justify-center bg-[#FCFBF8]">
                    <span className="text-6xl">🍷</span>
                  </div>

                  <div className="min-w-0 space-y-3 p-5">
                    <div className="min-w-0 space-y-2">
                      <p className="text-[11px] font-black tracking-[0.18em] text-[#B36262] uppercase">
                        AI RECOMMENDATION
                      </p>
                      <h3 className="text-text-main truncate text-[1.05rem] leading-none font-black">
                        {wine.nameKr}
                      </h3>
                      <p className="truncate text-xs font-bold text-[#FF8A00]">
                        {wine.recommendationReason || '당신을 위한 추천 와인'}
                      </p>
                    </div>

                    <div className="space-y-1.5 pt-1">
                      {[['MATCH', Math.floor(wine.matchRate / 20)]].map(([label, value]) => (
                        <div key={label} className="flex items-center justify-between">
                          <span className="text-text-main/40 text-[12px] font-black tracking-tighter">
                            {label}
                          </span>
                          <div className="flex gap-1.5">
                            {[1, 2, 3, 4, 5].map((diamond) => (
                              <div
                                key={diamond}
                                className={`h-2 w-2 rotate-45 ${
                                  diamond <= Number(value) ? 'bg-[#B36262]' : 'bg-primary-100'
                                }`}
                              />
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </article>
              </Link>
            ))
          ) : (
            <div className="text-text-main/40 w-full py-10 text-center font-bold">
              추천 와인이 없습니다.
            </div>
          )}
        </div>
      </section>

      <section className="px-6 py-3">
        <h2 className="text-text-main mb-4 text-xl font-black tracking-tight italic">
          형선&apos;S PICK!
        </h2>

        <div className="no-scrollbar mb-4 flex gap-3 overflow-x-auto pb-1">
          {SITUATIONS.map((category) => (
            <Chip
              key={category}
              active={activeSituation === category}
              variant={activeSituation === category ? 'primary' : 'secondary'}
              className="px-6 py-2 text-xs font-black transition-all"
              onClick={() => setActiveSituation(category)}
            >
              {category}
            </Chip>
          ))}
        </div>

        <div className="space-y-2">
          {situationWines.length > 0 ? (
            situationWines.map((wine) => (
              <Link key={wine.wineId} href={`/wines/${wine.wineId}`} className="block">
                <article className="border-primary-100 flex min-h-[5.8rem] items-center gap-3 rounded-[22px] border bg-white px-3.5 py-2.5 shadow-[0_10px_24px_rgba(51,34,17,0.05)] transition-all active:scale-[0.985]">
                  <div className="flex h-[4.15rem] w-[4.15rem] shrink-0 items-center justify-center rounded-[1.1rem] bg-[#FCE7E7]">
                    <span className="text-[1.65rem]">🍷</span>
                  </div>

                  <div className="min-w-0 flex-1">
                    <h3 className="text-text-main truncate text-[0.9rem] leading-tight font-black">
                      {wine.nameKr}
                    </h3>
                    <p className="text-text-main/42 mt-0.5 truncate text-[0.78rem] leading-tight font-medium">
                      {wine.recommendationReason || `${activeSituation} 상황에 추천하는 와인`}
                    </p>
                    <p className="mt-1.5 text-[0.9rem] leading-none font-black text-[#B36262]">
                      매칭률 {wine.matchRate}%
                    </p>
                  </div>

                  <ChevronRightIcon className="text-primary-100 shrink-0" size={18} />
                </article>
              </Link>
            ))
          ) : (
            <div className="text-text-main/40 py-10 text-center font-bold">
              해당 상황에 대한 추천 와인이 없습니다.
            </div>
          )}
        </div>
      </section>

      <style jsx global>{`
        .no-scrollbar::-webkit-scrollbar {
          display: none;
        }
        .no-scrollbar {
          -ms-overflow-style: none;
          scrollbar-width: none;
        }
      `}</style>
    </div>
  );
}

function ChevronRightIcon({ className, size }: { className?: string; size?: number }) {
  return (
    <svg
      className={className}
      width={size || 24}
      height={size || 24}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="3"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}
