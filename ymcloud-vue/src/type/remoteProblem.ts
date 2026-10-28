import type { ProblemCaseVO } from './types';

export type RemoteProblemRequest = {
  platform: string;
  problemId: string;
};

export type RemoteProblemDraft = {
  problemId: string;
  title: string;
  content: string;
  input: string;
  output: string;
  source: string;
  timeLimit: number | null;
  memoryLimit: number | null;
  notice?: string | null;
  problemCaseList: ProblemCaseVO[];
};
