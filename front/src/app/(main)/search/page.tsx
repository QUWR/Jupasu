'use client';

import { useState, useEffect, useRef } from 'react';
import { Search, SlidersHorizontal, X, Star, ChevronLeft } from 'lucide-react';
import Chip from '@/components/ui/chip/Chip';
import Link from 'next/link';
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
      const params: Record<string, any> = {
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
    <div className="min-h-screen bg-background flex flex-col pb-24 relative">
      <header className="px-6 pt-10 pb-4 space-y-4">
        <div>
          <h1 className="text-2xl font-black text-text-main italic tracking-tight uppercase">EXPLORE</h1>
          <p className="text-sm text-text-main/40 font-medium">새로운 와인을 발견해 보세요</p>
        </div>

        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-text-main/30" size={20} />
            <input
              className="w-full bg-white border-2 border-primary-100 rounded-full py-3 pl-12 pr-4 text-sm font-bold focus:outline-none focus:border-primary-500 transition-colors shadow-inner-sm placeholder:text-text-main/20"
              placeholder="와인 이름, 종류 검색..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={handleSearchKeyDown}
            />
          </div>

          <button
            onClick={() => setIsFilterOpen(true)}
            className="w-[52px] h-[52px] shrink-0 bg-white border-2 border-primary-100 rounded-full flex items-center justify-center text-text-main/40 shadow-sm cursor-pointer active:scale-95 transition-transform"
          >
            <SlidersHorizontal size={20} />
          </button>
        </div>

        <div className="flex items-center justify-end gap-2 pt-2">
          <div className="relative" ref={sortRef}>
            <button
              onClick={() => setIsSortOpen((prev) => !prev)}
              className="flex items-center gap-2 rounded-[0.95rem] bg-[#C96D72] px-4 py-2 text-[0.74rem] font-black text-white shadow-sm cursor-pointer"
            >
              {SORT_OPTIONS.find((opt) => opt.key === sortOption)?.label}
              <ChevronLeft
                size={13}
                className={cn('rotate-[270deg] transition-transform', isSortOpen && 'rotate-90')}
              />
            </button>

            {isSortOpen && (
              <div className="border-primary-100 absolute top-full right-0 z-30 mt-2 min-w-[6rem] rounded-[1rem] border bg-white p-1 shadow-xl animate-in fade-in zoom-in-95 duration-200">
                {SORT_OPTIONS.map((item) => (
                  <button
                    key={item.key}
                    onClick={() => {
                      setSortOption(item.key);
                      setIsSortOpen(false);
                    }}
                    className={cn(
                      'w-full rounded-[0.75rem] px-3 py-2 text-center text-[0.72rem] font-black cursor-pointer transition-colors',
                      sortOption === item.key ? 'bg-primary-100 text-[#C96D72]' : 'text-text-main/62 hover:bg-gray-50'
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

      <main className="flex-1 px-4 overflow-y-auto no-scrollbar pt-1">
        <div className="grid grid-cols-2 gap-3 items-start content-start">
          {wines.length > 0 ? (
            wines.map((wine) => (
              <Link
                key={wine.id}
                href={ROUTE_PATH.WINE_DETAIL(wine.id)}
                prefetch={false}
                className="bg-white rounded-[20px] border border-primary-100 shadow-sm flex flex-col overflow-hidden h-full active:scale-[0.98] transition-transform"
              >
                <div className="w-full aspect-square bg-white relative overflow-hidden flex items-center justify-center">
                  <img
                    src={
                      wine.imageUrl === '/images/default_wine.png'
                        ? DEFAULT_WINE_IMAGE_URL
                        : (wine.imageUrl || DEFAULT_WINE_IMAGE_URL)
                    }
                    alt={wine.nameKr || wine.nameEn}
                    className="w-[58%] h-[58%] sm:w-[70%] sm:h-[70%] object-contain"
                    onError={(e) => {
                      e.currentTarget.src = DEFAULT_WINE_IMAGE_URL;
                      e.currentTarget.onerror = null;
                    }}
                  />
                </div>

                <div className="p-3 flex flex-col flex-1 min-h-[110px]">
                  <h3 className="h-[2.8rem] font-black text-sm text-text-main leading-tight line-clamp-2">
                    {wine.nameKr || wine.nameEn}
                  </h3>

                  <p className="mt-1 text-[10px] text-text-main/40 font-bold truncate">
                    {wine.country || '원산지 미상'} • {wine.type}
                  </p>

                  <div className="mt-auto pt-2 flex items-center justify-between">
                    <div className="flex items-center gap-0.5 text-[#FF8A00] font-black text-xs">
                      <Star size={10} fill="#FF8A00" className="text-[#FF8A00]" />
                      {wine.averageRating?.toFixed(1) || '0.0'}
                    </div>

                    <span className="text-[11px] font-black text-text-main leading-none">
                      {wine.price ? `₩${wine.price.toLocaleString()}` : '-'}
                    </span>
                  </div>
                </div>
              </Link>
            ))
          ) : !isLoading ? (
            <div className="col-span-2 py-20 text-center">
              <p className="text-text-main/40 font-bold text-sm">검색 결과가 없습니다.</p>
            </div>
          ) : null}
        </div>

        {hasMore && wines.length > 0 && (
          <div className="py-8 flex justify-center relative z-10">
            <button
              onClick={() => fetchWines(page, true)}
              disabled={isLoading}
              className="bg-white border-2 border-primary-100 text-text-main font-black text-sm py-3 px-8 rounded-full shadow-sm active:scale-95 transition-all disabled:opacity-50 disabled:active:scale-100 cursor-pointer"
            >
              {isLoading ? '불러오는 중...' : '더 보기'}
            </button>
          </div>
        )}

        {isLoading && wines.length === 0 && (
          <div className="py-20 text-center">
            <p className="text-text-main/40 font-bold text-sm animate-pulse">와인 데이터를 불러오는 중입니다...</p>
          </div>
        )}
      </main>

      {isFilterOpen && (
        <div className="fixed inset-0 z-[100] bg-black/40 flex flex-col justify-end animate-in fade-in duration-300">
          <div className="absolute inset-0" onClick={() => setIsFilterOpen(false)} />

          <div className="relative bg-white rounded-t-[40px] p-8 space-y-8 animate-in slide-in-from-bottom-full duration-300 max-h-[90vh] overflow-y-auto no-scrollbar pb-[env(safe-area-inset-bottom,2rem)]">
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-black text-text-main">필터</h2>
              <button onClick={() => setIsFilterOpen(false)} className="text-text-main/30 cursor-pointer p-1">
                <X size={24} />
              </button>
            </div>

            <div className="space-y-3">
              <div className="flex items-center gap-1.5">
                <span className="text-sm font-black text-text-main">Wine types</span>
              </div>
              <div className="flex flex-wrap gap-2">
                {WINE_TYPES.map((type) => (
                  <Chip
                    key={type}
                    active={selectedType === type}
                    variant={selectedType === type ? 'primary' : 'secondary'}
                    onClick={() => toggleType(type)}
                    className="px-5 text-xs font-bold border-none cursor-pointer"
                  >
                    {type}
                  </Chip>
                ))}
              </div>
            </div>

            <div className="space-y-3">
              <span className="text-sm font-black text-text-main">Price Range</span>
              <div className="flex items-center gap-3">
                <div className="flex-1 bg-white border-2 border-primary-100 rounded-2xl p-2 flex items-center justify-center focus-within:border-[#B36262] transition-colors shadow-inner-sm">
                  <input
                    type="number"
                    value={priceRange.min}
                    onChange={(e) => setPriceRange({ ...priceRange, min: e.target.value })}
                    placeholder="최소"
                    className="w-full text-center font-black text-text-main outline-none placeholder:text-text-main/20 bg-transparent"
                  />
                </div>

                <span className="text-text-main/30 font-bold">~</span>

                <div className="flex-1 bg-white border-2 border-primary-100 rounded-2xl p-2 flex items-center justify-center focus-within:border-[#B36262] transition-colors shadow-inner-sm">
                  <input
                    type="number"
                    value={priceRange.max}
                    onChange={(e) => setPriceRange({ ...priceRange, max: e.target.value })}
                    placeholder="최대"
                    className="w-full text-center font-black text-text-main outline-none placeholder:text-text-main/20 bg-transparent"
                  />
                </div>

                <span className="text-sm font-bold text-text-main/40">만원</span>
              </div>
            </div>

            <div className="space-y-3">
              <span className="text-sm font-black text-text-main">Average Rating</span>
              <div className="flex items-center gap-4">
                <div className="flex gap-1">
                  {[1, 2, 3, 4, 5].map((s) => (
                    <button key={s} onClick={() => setSelectedRating(s)} className="cursor-pointer p-0.5">
                      <Star
                        size={30}
                        fill={s <= selectedRating ? '#B36262' : 'none'}
                        className={cn('transition-all', s <= selectedRating ? 'text-[#B36262]' : 'text-primary-100')}
                      />
                    </button>
                  ))}
                </div>
                <span className="text-lg font-black text-[#B36262] mt-1.5 w-8">
                  {selectedRating > 0 ? `${selectedRating}+` : ''}
                </span>
              </div>
            </div>

            <div className="pt-6 flex gap-3 border-t border-primary-100 bg-white sticky bottom-0 z-10">
              <button
                onClick={() => {
                  setSelectedType('');
                  setSelectedRating(0);
                  setPriceRange({ min: '', max: '' });
                }}
                className="flex-1 bg-white border border-primary-100 py-4 rounded-2xl text-sm font-black text-text-main/40 active:scale-95 transition-transform cursor-pointer"
              >
                초기화
              </button>

              <button
                onClick={() => {
                  setIsFilterOpen(false);
                  fetchWines(0, false);
                }}
                className="flex-[2] bg-[#B36262] text-white py-4 rounded-2xl text-sm font-black shadow-lg shadow-[#B36262]/20 active:scale-95 transition-transform cursor-pointer"
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