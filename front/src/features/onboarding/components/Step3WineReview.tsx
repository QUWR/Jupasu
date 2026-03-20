'use client';

import { useState } from 'react';
import { Search, Star, Loader2, MessageSquare, X } from 'lucide-react';
import { useQuery, useMutation } from '@tanstack/react-query';
import Button from '@/components/ui/button/Button';
import Modal from '@/components/ui/modal/Modal';
import { cn } from '@/lib/utils';
import { useDebounce } from '@/hooks/useDebounce';
import { useModal } from '@/hooks/useModal';
import { wineApi } from '@/features/wine/api/wine.api';
import { Wine } from '@/features/wine/types/wine.types';
import { reviewApi } from '@/features/review/api/review.api';
import Image from 'next/image';

interface Step3WineReviewProps {
  onNext: () => void;
  onPrev: () => void;
}

export default function Step3WineReview({ onNext }: Step3WineReviewProps) {
  const [searchTerm, setSearchTerm] = useState('');
  const debouncedSearchTerm = useDebounce(searchTerm, 500);
  const [ratings, setRatings] = useState<Record<number, number>>({});
  const [reviewedWines, setReviewedWines] = useState<
    Array<{ wine: Wine; rating: number; content: string; id?: number }>
  >([]);
  const [reviewContent, setReviewContent] = useState('');
  const { isOpen, open, close } = useModal();
  const [selectedWine, setSelectedWine] = useState<Wine | null>(null);

  // 1. 와인 검색 API 연동
  const { data, isLoading } = useQuery({
    queryKey: ['wines', 'search', debouncedSearchTerm],
    queryFn: () => wineApi.searchWines({ keyword: debouncedSearchTerm, size: 20 }),
    enabled: debouncedSearchTerm.length >= 1,
  });

  // 2. 리뷰 등록/수정 API 연동
  const createReviewMutation = useMutation({
    mutationFn: (data: { wineId: number; rating: number; content: string; wine?: Wine }) =>
      reviewApi.create(data),
    onSuccess: (response, variables) => {
      // response: ApiResponse<Long>
      const reviewId = response.data;
      const wineToUpdate = variables.wine || selectedWine;

      if (wineToUpdate) {
        setReviewedWines((prev) => [
          ...prev.filter((item) => item.wine.id !== wineToUpdate.id),
          {
            wine: wineToUpdate,
            rating: variables.rating,
            content: variables.content,
            id: reviewId,
          },
        ]);
      }
      setReviewContent('');
      close();
    },
    onError: (error) => {
      console.error('리뷰 등록 실패:', error);
    },
  });

  const updateReviewMutation = useMutation({
    mutationFn: (data: { wineId: number; reviewId: number; rating: number; content: string }) =>
      reviewApi.update(data),
    onSuccess: (_, variables) => {
      if (selectedWine) {
        setReviewedWines((prev) =>
          prev.map((item) =>
            item.wine.id === selectedWine.id
              ? { ...item, rating: variables.rating, content: variables.content }
              : item,
          ),
        );
      }
      setReviewContent('');
      close();
    },
    onError: (error) => {
      console.error('리뷰 수정 실패:', error);
    },
  });
  const deleteReviewMutation = useMutation({
    mutationFn: (data: { wineId: number; reviewId: number }) => reviewApi.delete(data),
    onSuccess: (_, variables) => {
      setReviewedWines((prev) => prev.filter((item) => item.wine.id !== variables.wineId));
      setRatings((prev) => {
        const next = { ...prev };
        delete next[variables.wineId];
        return next;
      });
    },
    onError: (error) => {
      console.error('리뷰 삭제 실패:', error);
    },
  });

  const handleDeleteReview = (e: React.MouseEvent, wineId: number, reviewId?: number) => {
    e.stopPropagation();

    if (!reviewId) {
      setReviewedWines((prev) => prev.filter((item) => item.wine.id !== wineId));
      setRatings((prev) => {
        const next = { ...prev };
        delete next[wineId];
        return next;
      });
    } else {
      deleteReviewMutation.mutate({ wineId, reviewId });
    }
  };

  const handleRate = (wine: Wine, score: number, e: React.MouseEvent | React.KeyboardEvent) => {
    e.stopPropagation();

    // 이미 통신 중인 경우 중복 요청 방지
    if (createReviewMutation.isPending || updateReviewMutation.isPending) return;

    const newScore = ratings[wine.id] === score ? 0 : score;

    setRatings((prev) => ({
      ...prev,
      [wine.id]: newScore,
    }));

    if (newScore > 0) {
      const existingReview = reviewedWines.find((item) => item.wine.id === wine.id);

      // Optimistic UI
      setReviewedWines((prev) => {
        if (existingReview) {
          return prev.map((item) =>
            item.wine.id === wine.id ? { ...item, rating: newScore } : item,
          );
        }
        return [...prev, { wine, rating: newScore, content: '' }];
      });

      if (existingReview?.id) {
        updateReviewMutation.mutate({
          wineId: wine.id,
          reviewId: existingReview.id,
          rating: newScore,
          content: existingReview.content || '',
        });
      } else {
        createReviewMutation.mutate({
          wineId: wine.id,
          rating: newScore,
          content: '',
          wine: wine,
        });
      }
    }
  };

  const handleWineClick = (wine: Wine) => {
    setSelectedWine(wine);
    const existing = reviewedWines.find((item) => item.wine.id === wine.id);
    setReviewContent(existing?.content || '');
    open();
  };

  const handleSaveReview = () => {
    if (!selectedWine) return;

    const rating = ratings[selectedWine.id] || 0;
    if (rating === 0) {
      alert('평점(별점)을 먼저 선택해주세요!');
      return;
    }

    const existingReview = reviewedWines.find((item) => item.wine.id === selectedWine.id);

    if (existingReview?.id) {
      updateReviewMutation.mutate({
        wineId: selectedWine.id,
        reviewId: existingReview.id,
        rating,
        content: reviewContent,
      });
    } else {
      createReviewMutation.mutate({
        wineId: selectedWine.id,
        rating,
        content: reviewContent,
        wine: selectedWine,
      });
    }
  };

  const wines = data?.content || [];

  return (
    <div className="animate-in fade-in slide-in-from-right-8 space-y-7 pb-10 duration-300">
      <div className="flex items-center justify-between">
        <h2 className="text-primary-700 font-bold">Step 3 - 시음 정보</h2>
        <span className="text-primary-700/60 text-sm font-bold">3 / 3</span>
      </div>

      <div className="space-y-6">
        <div className="space-y-1">
          <h3 className="text-2xl font-black tracking-tight">
            좋아하는 와인에 <br />
            평가를 남겨주세요
          </h3>
          <p className="text-text-main/40 text-sm font-bold">
            마셔본 와인을 검색하고 별점을 남겨보세요
          </p>
        </div>

        {/* Reviewed Wines Horizontal List */}
        {reviewedWines.length > 0 && (
          <div className="animate-in fade-in slide-in-from-left-4 space-y-3">
            <h4 className="text-primary-700 text-xs font-black tracking-wider uppercase">
              내가 평가한 와인 {reviewedWines.length}개
            </h4>
            <div className="scrollbar-hide -mx-6 flex gap-4 overflow-x-auto px-6 py-2">
              {reviewedWines.map(({ wine, rating }) => (
                <div
                  key={wine.id}
                  onClick={() => handleWineClick(wine)}
                  className="bg-primary-50/50 border-primary-100 relative flex w-36 shrink-0 cursor-pointer flex-col items-center gap-2 rounded-2xl border p-3 shadow-sm transition-transform active:scale-95"
                >
                  <div className="relative h-16 w-12 shrink-0">
                    <Image
                      src={wine.imageUrl || '/images/wine-placeholder.png'}
                      alt={wine.nameKr}
                      fill
                      className="object-contain"
                    />
                  </div>
                  <div className="w-full space-y-1 text-center">
                    <p className="text-text-main truncate text-[10px] font-bold">{wine.nameKr}</p>
                    <div className="flex justify-center gap-0.5">
                      {[1, 2, 3, 4, 5].map((s) => (
                        <Star
                          key={s}
                          size={10}
                          className={cn(
                            s <= rating ? 'fill-[#FDE047] text-[#FDE047]' : 'text-gray-200',
                          )}
                        />
                      ))}
                    </div>
                  </div>
                  <button
                    onClick={(e) =>
                      handleDeleteReview(
                        e,
                        wine.id,
                        reviewedWines.find((item) => item.wine.id === wine.id)?.id,
                      )
                    }
                    className="bg-primary-500 absolute -top-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full text-white shadow-sm transition-colors hover:bg-red-500"
                  >
                    <X size={12} className="stroke-[3]" />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Search Bar */}
        <div className="group relative">
          <Search
            className="text-text-main/30 group-focus-within:text-primary-700 absolute top-1/2 left-4 -translate-y-1/2 transition-colors"
            size={20}
          />
          <input
            className="border-primary-100 focus:border-primary-500 w-full rounded-2xl border-2 bg-white py-4 pr-12 pl-12 font-bold transition-all placeholder:text-gray-300 focus:outline-none"
            placeholder="와인 이름으로 검색"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          {isLoading && (
            <div className="absolute top-1/2 right-4 -translate-y-1/2">
              <Loader2 className="text-primary-500 animate-spin" size={20} />
            </div>
          )}
        </div>

        {/* Wine Results List */}
        <div className="min-h-[300px] space-y-3 pb-4">
          {wines.length > 0 ? (
            wines.map((wine) => (
              <div
                key={wine.id}
                onClick={() => handleWineClick(wine)}
                className="border-primary-100 animate-in zoom-in-95 hover:border-primary-300 hover:bg-primary-50/30 flex cursor-pointer items-center gap-4 rounded-3xl border bg-white p-4 shadow-sm transition-all duration-200 active:scale-[0.98]"
              >
                <div className="relative flex h-20 w-16 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-gray-50 p-2">
                  <Image
                    src={wine.imageUrl || '/images/wine-placeholder.png'}
                    alt={wine.nameKr}
                    fill
                    className="object-contain p-1"
                  />
                </div>
                <div className="flex-1 space-y-1 overflow-hidden">
                  <div className="flex items-center gap-2">
                    <h4 className="text-text-main truncate text-sm font-black">{wine.nameKr}</h4>
                    {reviewedWines.some((item) => item.wine.id === wine.id) && (
                      <span className="shrink-0 rounded-md bg-green-100 px-1.5 py-0.5 text-[9px] font-black text-green-700">
                        평가 완료
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="bg-primary-100 text-primary-800 rounded-lg px-2 py-0.5 text-[10px] font-bold">
                      {wine.type}
                    </span>
                    <span className="text-text-main/40 text-[10px] font-bold">{wine.country}</span>
                  </div>

                  {/* Rating Preview */}
                  <div className="flex gap-0.5 pt-1">
                    {[1, 2, 3, 4, 5].map((star) => {
                      const isFilled = star <= (ratings[wine.id] || 0);
                      return (
                        <Star
                          key={star}
                          onClick={(e) => handleRate(wine, star, e)}
                          size={18}
                          className={cn(
                            'cursor-pointer transition-all duration-200 hover:scale-125',
                            isFilled ? 'fill-[#FDE047] text-[#FDE047]' : 'text-gray-200',
                          )}
                        />
                      );
                    })}
                  </div>
                </div>
                <div className="text-primary-500 flex h-8 w-8 items-center justify-center rounded-full bg-gray-50">
                  <MessageSquare size={16} />
                </div>
              </div>
            ))
          ) : searchTerm.length > 0 && !isLoading && debouncedSearchTerm === searchTerm ? (
            <div className="flex flex-col items-center justify-center py-20 text-center">
              <span className="mb-2 text-4xl">🔎</span>
              <p className="text-text-main/30 font-bold">검색 결과가 없어요.</p>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center py-20 text-center">
              <span className="mb-2 text-4xl">🍷</span>
              <p className="text-text-main/30 font-bold">와인 이름을 검색해보세요!</p>
            </div>
          )}
        </div>
      </div>

      <div className="space-y-4 pt-4">
        <Button onClick={onNext} size="full" className="h-14 text-lg font-bold shadow-lg">
          가입 완료
        </Button>
        <button
          onClick={onNext}
          className="text-text-main/40 hover:text-text-main w-full text-sm font-bold transition-colors"
        >
          나중에 기록할게요
        </button>
      </div>

      {/* Review Write Modal */}
      <Modal
        isOpen={isOpen}
        onClose={close}
        title="와인 리뷰 남기기"
        footer={
          <div className="flex w-full gap-3">
            <Button variant="outline" size="full" onClick={close} className="font-bold">
              취소
            </Button>
            <Button
              size="full"
              className="font-bold"
              isLoading={createReviewMutation.isPending}
              onClick={handleSaveReview}
            >
              등록
            </Button>
          </div>
        }
      >
        <div className="space-y-6">
          <div className="flex items-center gap-4">
            <div className="relative h-20 w-16 shrink-0 rounded-xl bg-gray-50 p-2">
              <Image
                src={selectedWine?.imageUrl || '/images/wine-placeholder.png'}
                alt={selectedWine?.nameKr || ''}
                fill
                className="object-contain p-1"
              />
            </div>
            <div className="overflow-hidden">
              <h4 className="text-text-main truncate text-lg font-black">{selectedWine?.nameKr}</h4>
              <p className="text-text-main/40 truncate text-xs font-bold">{selectedWine?.nameEn}</p>
            </div>
          </div>

          <div className="space-y-3">
            <p className="text-text-main text-center text-sm font-bold">와인은 어떠셨나요?</p>
            <div className="flex justify-center gap-2">
              {[1, 2, 3, 4, 5].map((star) => {
                const isFilled = star <= (ratings[selectedWine?.id || 0] || 0);
                return (
                  <button
                    key={star}
                    onClick={(e) => handleRate(selectedWine!, star, e)}
                    className="transition-transform active:scale-125"
                  >
                    <Star
                      size={32}
                      className={cn(
                        'transition-all duration-200',
                        isFilled ? 'fill-[#FDE047] text-[#FDE047]' : 'text-gray-300',
                      )}
                    />
                  </button>
                );
              })}
            </div>
          </div>

          <div className="space-y-2">
            <label className="text-text-main text-sm font-bold">상세 소감 (선택)</label>
            <textarea
              className="border-primary-100 focus:border-primary-500 min-h-[120px] w-full resize-none rounded-2xl border-2 bg-gray-50/50 p-4 font-bold transition-all focus:outline-none"
              placeholder="와인의 맛, 향, 분위기 등을 자유롭게 적어주세요."
              value={reviewContent}
              onChange={(e) => setReviewContent(e.target.value)}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
