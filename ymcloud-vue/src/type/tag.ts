import type { Tag } from './types';

export type TagDTO = Pick<Tag, 'name' | 'color' | 'parentId'>;

export type TagClassificationDTO = {
  name: string;
  priority: number;
};

export type TagClassification = TagClassificationDTO & {
  id: number;
};

export type TagsWithClassification = {
  classification: TagClassification | null;
  tagList: Tag[];
};

export type TagResult<T> = {
  code: number;
  msg: string;
  data: T;
};
