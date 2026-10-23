export interface Announcement {
  id: number;
  title: string;
  content: string;
  pinned: boolean;
  authorUsername: string;
  updatedTime: string;
}

export interface AnnouncementPageQuery {
  pageNo: number;
}

export interface AnnouncementPageResult {
  code: number;
  msg: string;
  data: {
    total: number;
    pages: number;
    records: Announcement[];
  };
}
