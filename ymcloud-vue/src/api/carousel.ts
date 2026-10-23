import request from './request';
import type { AxiosResponse } from 'axios';
import type { SystemSettingResult } from '@/type/types';
import type { CarouselImage, CarouselSetting } from '@/type/carousel';

export function getPublicCarousel(): Promise<AxiosResponse<SystemSettingResult<CarouselSetting>>> {
  return request({ url: '/public/carousel' });
}

export function getAdminCarousel(): Promise<AxiosResponse<SystemSettingResult<CarouselSetting>>> {
  return request({ url: '/admin/system-setting/carousel' });
}

export function saveCarousel(fileIds: string[]): Promise<AxiosResponse<SystemSettingResult<null>>> {
  return request({ url: '/admin/system-setting/carousel', method: 'PUT', data: { fileIds } });
}

export function uploadCarousel(file: Blob): Promise<AxiosResponse<SystemSettingResult<CarouselImage>>> {
  const data = new FormData();
  data.append('file', file, 'carousel.png');
  return request({ url: '/admin/system-setting/carousel/image', method: 'POST', data, timeout: 120000 });
}
