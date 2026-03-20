'use client';

import { useState, useEffect, useRef } from 'react';
import { Search, SlidersHorizontal, X, Star, ChevronLeft } from 'lucide-react';
import Chip from '@/components/ui/chip/Chip';
import Link from 'next/link';
import Image from 'next/image';
import { ROUTE_PATH } from '@/constants/route-path';
import { cn } from '@/lib/utils';
import { api } from '@/lib/axios';

const WINE_TYPES = ['RED', 'WHITE', 'SPARKLING', 'ROSE', 'DESSERT', 'FORTIFIED'];

const SORT_OPTIONS = [
  { key: 'RECOMMEND', label: '추천순' },
  { key: 'price,asc', label: '가격 낮은순' },
  { key: 'price,desc', label: '가격 높은순' },
  { key: 'rating,desc', label: '평점 높은순' },
];

const DEFAULT_WINE_IMAGE_URL = '/default_wine.png';

interface WineSearchResponse {
  id: number;
  nameKr: string;
  nameEn: string;
  type: string;
  country: string;
  averageRating: number;
  price: number;
  imageUrl: string;
}

export default function SearchPage() {
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const [wines, setWines] = useState<WineSearchResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);

  const [sortOption, setSortOption] = useState('RECOMMEND');
  const [isSortOpen, setIsSortOpen] = useState(false);
  const sortRef = useRef<HTMLDivElement>(null);

  const [selectedType, setSelectedType] = useState<string>('');
  const [selectedRating, setSelectedRating] = useState(0);
  const [priceRange, setPriceRange] = useState({ min: '', max: '' });

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (sortRef.current && !sortRef.current.contains(event.target as Node)) {
        setIsSortOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const fetchWines = async (currentPage = 0, append = false) => {
    setIsLoading(true);
    try {
      const params: Record<string, string | number | undefined> = {
        page: currentPage,
        size: 10,
      };

      if (sortOption !== 'RECOMMEND') {
        params.sort = sortOption;
      }

      if (searchQuery) params.keyword = searchQuery;
      if (selectedType) params.type = selectedType;

      if (priceRange.min !== '') params.minPrice = Number(priceRange.min) * 10000;
      if (priceRange.max !== '') params.maxPrice = Number(priceRange.max) * 10000;

      if (selectedRating > 0) params.minRate = selectedRating;

      const response = await api.get('/wines', { params });

      const responseData = response.data?.data;
      const content = responseData?.content || [];
      const isLast = responseData?.last ?? true;

      if (append) {
        setWines((prev) => [...prev, ...content]);
      } else {
        setWines(content);
      }

      setHasMore(!isLast);
      setPage(currentPage + 1);
    } catch (error) {
      console.error('와인 데이터를 불러오는데 실패했습니다:', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchWines(0, false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sortOption]);

  const handleSearchKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      fetchWines(0, false);
    }
  };

  const toggleType = (type: string) => {
    setSelectedType((prev) => (prev === type ? '' : type));
  };

  return (
    // ⭐️ [수정] 전체 배경색 원복: bg-[#FAFAFA] -> bg-background
    <div className="bg-background relative flex min-h-screen flex-col pb-24">
      {/* Header */}
      {/* ⭐️ [수정] 헤더 패딩 및 스타일 원복: px-5, pt-[calc...], bg-[#FAFAFA] -> px-6, pt-10, bg-transparent */}
      <header className="space-y-4 px-6 pt-10 pb-4">
        <div>
          <h1 className="text-text-main text-2xl font-black tracking-tight uppercase italic">
            EXPLORE
          </h1>
          <p className="text-text-main/40 text-sm font-medium">새로운 와인을 발견해 보세요</p>
        </div>

        {/* 검색창과 필터 버튼 */}
        {/* ⭐️ [수정] 간격 원복: gap-2.5 -> gap-2 */}
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search
              className="text-text-main/30 absolute top-1/2 left-4 -translate-y-1/2"
              size={20}
            />
            <input
              // ⭐️ [수정] 인풋 스타일 원복
              className="border-primary-100 focus:border-primary-500 shadow-inner-sm placeholder:text-text-main/20 w-full rounded-full border-2 bg-white py-3 pr-4 pl-12 text-sm font-bold transition-colors focus:outline-none"
              placeholder="와인 이름, 종류 검색..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={handleSearchKeyDown}
            />
          </div>
          <button
            onClick={() => setIsFilterOpen(true)}
            // ⭐️ [수정] 버튼 스타일 원복
            className="border-primary-100 text-text-main/40 flex h-[52px] w-[52px] shrink-0 cursor-pointer items-center justify-center rounded-full border-2 bg-white shadow-sm transition-transform active:scale-95"
          >
            <SlidersHorizontal size={20} />
          </button>
        </div>

        {/* 오른쪽 정렬 버튼 */}
        {/* ⭐️ [수정] 정렬 버튼 원복 */}
        <div className="flex items-center justify-end gap-2 pt-2">
          <div className="relative" ref={sortRef}>
            <button
              onClick={() => setIsSortOpen((prev) => !prev)}
              className="flex cursor-pointer items-center gap-2 rounded-[0.95rem] bg-[#C96D72] px-4 py-2 text-[0.74rem] font-black text-white shadow-sm"
            >
              {SORT_OPTIONS.find((opt) => opt.key === sortOption)?.label}
              <ChevronLeft
                size={13}
                className={cn('rotate-[270deg] transition-transform', isSortOpen && 'rotate-90')}
              />
            </button>

            {isSortOpen && (
              <div className="border-primary-100 animate-in fade-in zoom-in-95 absolute top-full right-0 z-30 mt-2 min-w-[6rem] rounded-[1rem] border bg-white p-1 shadow-xl duration-200">
                {SORT_OPTIONS.map((item) => (
                  <button
                    key={item.key}
                    onClick={() => {
                      setSortOption(item.key);
                      setIsSortOpen(false);
                    }}
                    className={cn(
                      'w-full cursor-pointer rounded-[0.75rem] px-3 py-2 text-center text-[0.72rem] font-black transition-colors',
                      sortOption === item.key
                        ? 'bg-primary-100 text-[#C96D72]'
                        : 'text-text-main/62 hover:bg-gray-50',
                    )}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Wine Grid */}
      {/* ⭐️ [수정] 메인 패딩 원복: px-5 -> px-4 */}
      <main className="no-scrollbar flex-1 overflow-y-auto px-4 pt-1">
        {/* ⭐️ [수정] 간격 원복: gap-3.5 -> gap-3 */}
        <div className="grid grid-cols-2 content-start items-start gap-3">
          {wines.length > 0 ? (
            wines.map((wine) => (
              <Link
                key={wine.id}
                href={ROUTE_PATH.WINE_DETAIL(wine.id)}
                prefetch={false}
                // ⭐️ [수정] 카드 스타일 원복
                className="border-primary-100 flex flex-col overflow-hidden rounded-[20px] border bg-white shadow-sm transition-transform active:scale-[0.98]"
              >
                {/* 이미지 영역 */}
                {/* ⭐️ [핵심 수정 유지] 사진 부분만 잘 보이도록 p-4 여백 추가 유지 및 비율 조정 (aspect-square) */}
                <div className="relative flex aspect-square w-full items-center justify-center overflow-hidden bg-white p-4">
                  <Image
                    src={
                      wine.imageUrl === '/images/default_wine.png'
                        ? DEFAULT_WINE_IMAGE_URL
                        : wine.imageUrl || DEFAULT_WINE_IMAGE_URL
                    }
                    alt={wine.nameKr || wine.nameEn}
                    fill
                    // ⭐️ [핵심 수정 유지] object-contain 속성 유지
                    className="object-contain p-4"
                    sizes="(max-width: 768px) 50vw, 25vw"
                  />
                </div>

                {/* 텍스트 영역 */}
                {/* ⭐️ [수정] 텍스트 영역 패딩 및 폰트 사이즈 원복 */}
                <div className="space-y-1.5 p-3">
                  <h3 className="text-text-main line-clamp-2 text-sm leading-tight font-black">
                    {wine.nameKr || wine.nameEn}
                  </h3>
                  <p className="text-text-main/40 truncate text-[10px] font-bold">
                    {wine.country || '원산지 미상'} • {wine.type}
                  </p>
                  <div className="flex items-center justify-between pt-0.5">
                    <div className="flex items-center gap-0.5 text-xs font-black text-[#FF8A00]">
                      <Star size={10} fill="#FF8A00" className="text-[#FF8A00]" />{' '}
                      {wine.averageRating?.toFixed(1) || '0.0'}
                    </div>
                    <span className="text-text-main mt-0.5 text-[11px] leading-none font-black">
                      {wine.price ? `₩${wine.price.toLocaleString()}` : '-'}
                    </span>
                  </div>
                </div>
              </Link>
            ))
          ) : !isLoading ? (
            <div className="col-span-2 py-20 text-center">
              <p className="text-text-main/40 text-sm font-bold">검색 결과가 없습니다.</p>
            </div>
          ) : null}
        </div>

        {/* 더 보기(Load More) 버튼 */}
        {hasMore && wines.length > 0 && (
          <div className="relative z-10 flex justify-center py-8">
            <button
              onClick={() => fetchWines(page, true)}
              disabled={isLoading}
              className="border-primary-100 text-text-main cursor-pointer rounded-full border-2 bg-white px-8 py-3 text-sm font-black shadow-sm transition-all active:scale-95 disabled:opacity-50 disabled:active:scale-100"
            >
              {isLoading ? '불러오는 중...' : '더 보기'}
            </button>
          </div>
        )}

        {/* 초기 로딩 시 메시지 */}
        {isLoading && wines.length === 0 && (
          <div className="py-20 text-center">
            <p className="text-text-main/40 animate-pulse text-sm font-bold">
              와인 데이터를 불러오는 중입니다...
            </p>
          </div>
        )}
      </main>

      {/* Filter Bottom Sheet Overlay */}
      {isFilterOpen && (
        <div className="animate-in fade-in fixed inset-0 z-[100] flex flex-col justify-end bg-black/40 duration-300">
          <div className="absolute inset-0" onClick={() => setIsFilterOpen(false)} />
          {/* ⭐️ [수정] 바텀 시트 스타일 원복 */}
          <div className="animate-in slide-in-from-bottom-full no-scrollbar relative max-h-[90vh] space-y-8 overflow-y-auto rounded-t-[40px] bg-white p-8 pb-[env(safe-area-inset-bottom,2rem)] duration-300">
            <div className="flex items-center justify-between">
              <h2 className="text-text-main text-xl font-black">필터</h2>
              <button
                onClick={() => setIsFilterOpen(false)}
                className="text-text-main/30 cursor-pointer p-1"
              >
                <X size={24} />
              </button>
            </div>

            {/* Wine Types */}
            <div className="space-y-3">
              <div className="flex items-center gap-1.5">
                <span className="text-text-main text-sm font-black">Wine types</span>
              </div>
              <div className="flex flex-wrap gap-2">
                {WINE_TYPES.map((type) => (
                  <Chip
                    key={type}
                    active={selectedType === type}
                    variant={selectedType === type ? 'primary' : 'secondary'}
                    onClick={() => toggleType(type)}
                    className="cursor-pointer border-none px-5 text-xs font-bold"
                  >
                    {type}
                  </Chip>
                ))}
              </div>
            </div>

            {/* Price Range */}
            <div className="space-y-3">
              <span className="text-text-main text-sm font-black">Price Range</span>
              <div className="flex items-center gap-3">
                {/* ⭐️ [수정] 가격 입력창 스타일 원복 */}
                <div className="border-primary-100 shadow-inner-sm flex flex-1 items-center justify-center rounded-2xl border-2 bg-white p-2 transition-colors focus-within:border-[#B36262]">
                  <input
                    type="number"
                    value={priceRange.min}
                    onChange={(e) => setPriceRange({ ...priceRange, min: e.target.value })}
                    placeholder="최소"
                    className="text-text-main placeholder:text-text-main/20 w-full bg-transparent text-center font-black outline-none"
                  />
                </div>
                <span className="text-text-main/30 font-bold">~</span>
                <div className="border-primary-100 shadow-inner-sm flex flex-1 items-center justify-center rounded-2xl border-2 bg-white p-2 transition-colors focus-within:border-[#B36262]">
                  <input
                    type="number"
                    value={priceRange.max}
                    onChange={(e) => setPriceRange({ ...priceRange, max: e.target.value })}
                    placeholder="최대"
                    className="text-text-main placeholder:text-text-main/20 w-full bg-transparent text-center font-black outline-none"
                  />
                </div>
                <span className="text-text-main/40 text-sm font-bold">만원</span>
              </div>
            </div>

            {/* Average Rating */}
            <div className="space-y-3">
              <span className="text-text-main text-sm font-black">Average Rating</span>
              <div className="flex items-center gap-4">
                <div className="flex gap-1">
                  {[1, 2, 3, 4, 5].map((s) => (
                    <button
                      key={s}
                      onClick={() => setSelectedRating(s)}
                      className="cursor-pointer p-0.5"
                    >
                      <Star
                        size={30}
                        fill={s <= selectedRating ? '#B36262' : 'none'}
                        className={cn(
                          'transition-all',
                          s <= selectedRating ? 'text-[#B36262]' : 'text-primary-100',
                        )}
                      />
                    </button>
                  ))}
                </div>
                <span className="mt-1.5 w-8 text-lg font-black text-[#B36262]">
                  {selectedRating > 0 ? `${selectedRating}+` : ''}
                </span>
              </div>
            </div>

            {/* Reset & Apply Buttons */}
            <div className="border-primary-100 sticky bottom-0 z-10 flex gap-3 border-t bg-white pt-6">
              <button
                onClick={() => {
                  setSelectedType('');
                  setSelectedRating(0);
                  setPriceRange({ min: '', max: '' });
                }}
                className="border-primary-100 text-text-main/40 flex-1 cursor-pointer rounded-2xl border bg-white py-4 text-sm font-black transition-transform active:scale-95"
              >
                초기화
              </button>
              <button
                onClick={() => {
                  setIsFilterOpen(false);
                  fetchWines(0, false);
                }}
                className="flex-[2] cursor-pointer rounded-2xl bg-[#B36262] py-4 text-sm font-black text-white shadow-lg shadow-[#B36262]/20 transition-transform active:scale-95"
              >
                필터 적용하기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
