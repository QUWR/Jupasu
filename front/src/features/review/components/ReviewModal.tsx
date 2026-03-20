'use client';

import { useState } from 'react';
import { Star, X } from 'lucide-react';
import Button from '@/components/ui/button/Button';
import { cn } from '@/lib/utils';
import Link from 'next/link';

interface ReviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: { rating: number; content: string }) => void;
  onDelete?: () => void;
  initialData?: { rating: number; content: string };
  wineInfo: { id?: number; name: string; category: string; image?: string };
}

export default function ReviewModal({
  isOpen,
  onClose,
  onSubmit,
  initialData,
  wineInfo,
}: ReviewModalProps) {
  const [rating, setRating] = useState(initialData?.rating || 0);
  const [content, setContent] = useState(initialData?.content || '');
  const isEdit = !!initialData;

  if (!isOpen) return null;

  const isValid = rating > 0 && content.trim().length >= 10;

  return (
    <div className="animate-in fade-in fixed inset-0 z-[120] flex items-center justify-center bg-black/45 px-4 duration-200">
      <button className="absolute inset-0" aria-label="리뷰 모달 닫기" onClick={onClose} />

      <div className="animate-in zoom-in-95 relative z-10 w-full max-w-[20.5rem] rounded-[1.7rem] bg-[#F7F5F1] px-3.5 pt-5 pb-5 shadow-[0_20px_54px_rgba(51,34,17,0.14)] duration-200">
        <header className="relative mb-5 flex items-center justify-center">
          <h2 className="text-text-main text-center text-[1rem] font-black tracking-[-0.03em]">
            {isEdit ? '리뷰 수정' : '리뷰 작성'}
          </h2>

          <button
            onClick={onClose}
            className="text-text-main/30 hover:text-text-main/45 absolute top-1/2 right-0 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full transition-colors hover:bg-white/70"
          >
            <X size={20} />
          </button>
        </header>

        <div className="space-y-4">
          <div className="border-primary-100 rounded-[1.2rem] border bg-white p-2.5 shadow-sm">
            <div className="flex items-center gap-2.5">
              <div className="flex h-14 w-10 shrink-0 items-center justify-center rounded-[0.75rem] bg-[#DDD2C1] text-[1.2rem]">
                🍷
              </div>

              <div className="min-w-0 flex-1">
                <Link
                  href={`/wines/${wineInfo.id}`}
                  onClick={onClose} // 이동 시 모달을 닫아줍니다.
                  className="text-text-main cursor-pointer truncate text-sm font-black hover:underline"
                >
                  {wineInfo.name}
                </Link>
                <p className="text-text-main/35 mt-0.5 text-xs font-medium">{wineInfo.category}</p>
              </div>

              {isEdit ? (
                <span className="text-text-main/35 rounded-full bg-[#F3EFE8] px-2 py-1 text-[10px] font-black">
                  수정 중
                </span>
              ) : null}
            </div>
          </div>

          <div>
            <h3 className="text-text-main text-[15px] font-black">
              {isEdit ? '별점' : '별점을 선택해주세요'}
            </h3>
            <div className="mt-2.5 flex justify-center gap-1">
              {[1, 2, 3, 4, 5].map((star) => (
                <button key={star} type="button" onClick={() => setRating(star)} className="p-0.5">
                  <Star
                    size={30}
                    fill={star <= rating ? '#D89A4F' : '#F0EAE1'}
                    className={star <= rating ? 'text-[#D89A4F]' : 'text-[#F0EAE1]'}
                  />
                </button>
              ))}
            </div>
          </div>

          <div>
            <h3 className="text-text-main text-[15px] font-black">
              {isEdit ? '리뷰 내용' : '리뷰를 작성해주세요'}
            </h3>
            <div className="relative mt-2.5">
              <textarea
                value={content}
                onChange={(event) => setContent(event.target.value.slice(0, 500))}
                className="border-primary-100 text-text-main placeholder:text-text-main/22 focus:border-primary-500 h-28 w-full resize-none rounded-[1.2rem] border bg-white p-3.5 text-[13px] leading-relaxed font-medium transition-colors outline-none"
                placeholder="이 와인은 어떠셨나요? 맛, 향, 분위기 등을 자유롭게 남겨주세요."
              />
              <span className="text-text-main/30 absolute right-4 bottom-4 text-[10px] font-bold">
                {content.length}/500
              </span>
            </div>
          </div>

          <div className="pt-2">
            <Button
              size="full"
              onClick={() => onSubmit({ rating, content })}
              disabled={!isValid}
              className={cn(
                'h-11 rounded-[1rem] text-sm font-black shadow-none',
                isValid ? 'bg-[#B27D7D] text-white hover:bg-[#9C6A6A]' : 'bg-[#D9D9DF] text-white',
              )}
            >
              {isEdit ? '리뷰 수정하기' : '리뷰 등록하기'}
            </Button>
          </div>

          {!isValid ? (
            <p className="text-text-main/28 text-center text-[10px] font-medium">
              별점과 10자 이상의 리뷰를 작성해주세요
            </p>
          ) : null}
        </div>
      </div>
    </div>
  );
}
