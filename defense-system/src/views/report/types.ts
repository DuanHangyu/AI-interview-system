export interface ReportDimension {
  name: string;
  aiScore: number;
  score: number;
  maxScore?: number | null;
  description?: string;
}

export interface ReportQa {
  id: number;
  question?: string;
  answer?: string;
  answerFile?: string | null;
  followQuestion?: string | null;
  followAnswer?: string | null;
  followAnswerFile?: string | null;
  strength?: string | null;
  weakness?: string | null;
  suggestion?: string | null;
}

export interface ReportDetail {
  id: number;
  studentName?: string;
  studentAccount?: string;
  schoolClass?: string;
  theme?: string;
  interviewTime?: string;
  defenseEnabled?: boolean;
  questionEnabled?: boolean;
  score?: number | null;
  aiScore?: number | null;
  totalScore?: number;
  passScore?: number;
  summary?: string | null;
  dimensions?: ReportDimension[] | null;
  analysis?: { suggestions?: string[]; strengths?: string[]; weaknesses?: string[] } | null;
  defense?: {
    defenseAnswer?: string;
    defenseAnswerFile?: string | null;
    strength?: string | null;
    weakness?: string | null;
    suggestion?: string | null;
  } | null;
  result?: ReportQa[] | null;
  review?: {
    comment?: string | null;
    reviewerName?: string | null;
    reviewTime?: string | null;
    dimensions?: { name: string; score: number }[];
  } | null;
  showResult?: boolean;
  state?: number;
  reAnalysis?: boolean;
}
