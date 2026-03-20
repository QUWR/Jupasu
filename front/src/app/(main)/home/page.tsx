'use client';

import { useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import Chip from '@/components/ui/chip/Chip';
import { useWineQuickRecommendQuery } from '@/features/wine/hooks/useWineListQuery';
import { DrinkingSituation } from '@/features/wine/types/wine.types';
import { ChevronRight } from 'lucide-react';

const DEFAULT_WINE_IMAGE_URL = '/default_wine.png';

const SITUATION_MAP: Record<string, DrinkingSituation> = {
  혼술: 'ALONE',
  기념일: 'DATE',
  파티: 'PARTY',
};

const SITUATIONS = ['혼술', '기념일', '파티'];

// 다이아몬드 게이지 표시 컴포넌트
function TasteGauge({ label, value }: { label: string; value: number | undefined | null }) {
  // 백엔드 데이터가 0.0 ~ 5.0 범위이므로 Math.ceil을 사용하여 1칸부터 채워지도록 함 (0이면 0칸)
  const displayValue = value ? Math.ceil(value) : 0;
  return (
    <div className="flex items-center justify-between">
      <span className="text-text-main/40 text-[10px] font-black tracking-tighter uppercase">
        {label}
      </span>
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5].map((level) => (
          <div
            key={level}
            className={`h-1.5 w-1.5 rotate-45 ${
              level <= displayValue ? 'bg-[#B36262]' : 'bg-primary-100'
            }`}
          />
        ))}
      </div>
    </div>
  );
}

function WineImage({ src, alt, className }: { src?: string; alt: string; className?: string }) {
  const [imgSrc, setImgSrc] = useState(src || DEFAULT_WINE_IMAGE_URL);

  return (
    <img
      src={imgSrc}
      alt={alt}
      className={className}
      onError={() => setImgSrc(DEFAULT_WINE_IMAGE_URL)}
    />
  );
}

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

                  <div className="flex aspect-[0.92] items-center justify-center bg-[#FCFBF8] p-6">
                    <WineImage
                      src={wine.imageUrl}
                      alt={wine.nameKr}
                      className="max-h-full max-w-full object-contain"
                    />
                  </div>

                  <div className="min-w-0 space-y-3 p-5">
                    <div className="min-w-0 space-y-1.5">
                      <p className="h-[15px] truncate text-[10px] font-black tracking-[0.1em] text-[#B36262] uppercase">
                        {wine.style || ''}
                      </p>
                      <h3 className="text-text-main truncate text-[1rem] leading-tight font-black">
                        {wine.nameKr}
                      </h3>
                      <p className="truncate text-[11px] font-bold text-[#FF8A00]">
                        {wine.recommendationReason || '당신을 위한 추천 와인'}
                      </p>
                    </div>

                    <div className="border-primary-100/50 space-y-1 border-t pt-1">
                      <TasteGauge label="SWEET" value={wine.sweetness} />
                      <TasteGauge label="ACID" value={wine.acidity} />
                      <TasteGauge label="BODY" value={wine.body} />
                      <TasteGauge label="TANNIN" value={wine.tannin} />
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
                <article className="border-primary-100 flex min-h-[6.5rem] items-center gap-3 rounded-[22px] border bg-white px-3.5 py-2.5 shadow-[0_10px_24px_rgba(51,34,17,0.05)] transition-all active:scale-[0.985]">
                  <div className="flex h-[4.8rem] w-[4.8rem] shrink-0 items-center justify-center rounded-[1.1rem] bg-[#FCE7E7] p-2">
                    <WineImage
                      src={wine.imageUrl}
                      alt={wine.nameKr}
                      className="max-h-full max-w-full object-contain"
                    />
                  </div>

                  <div className="min-w-0 flex-1">
                    <h3 className="text-text-main truncate text-[0.9rem] leading-tight font-black">
                      {wine.nameKr}
                    </h3>
                    <p className="text-text-main/42 mt-1 truncate text-[0.75rem] leading-tight font-medium">
                      {wine.recommendationReason || `${activeSituation} 상황에 추천하는 와인`}
                    </p>
                    <div className="mt-2 flex items-center justify-between">
                      <span className="text-[0.8rem] font-black text-[#B36262]">
                        {wine.matchRate}% MATCH
                      </span>
                    </div>
                  </div>

                  <ChevronRight className="text-primary-100 shrink-0" size={18} />
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
